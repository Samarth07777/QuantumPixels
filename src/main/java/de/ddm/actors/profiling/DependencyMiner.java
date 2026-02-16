package de.ddm.actors.profiling;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import de.ddm.actors.patterns.LargeMessageProxy;
import de.ddm.serialization.AkkaSerializable;
import de.ddm.singletons.InputConfigurationSingleton;
import de.ddm.structures.InclusionDependency;
import lombok.*;

import java.io.File;
import java.util.*;

public class DependencyMiner extends AbstractBehavior<DependencyMiner.Message> {

    public interface Message extends AkkaSerializable, LargeMessageProxy.LargeMessage {}

    @NoArgsConstructor
    public static class StartMessage implements Message {
        private static final long serialVersionUID = 1L;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HeaderMessage implements Message {
        private static final long serialVersionUID = 1L;
        private int id;
        private String[] header;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchMessage implements Message {
        private static final long serialVersionUID = 1L;
        private int id;
        private List<String[]> batch;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegistrationMessage implements Message {
        private static final long serialVersionUID = 1L;
        private ActorRef<DependencyWorker.Message> dependencyWorker;
    }

    // FIXED: Only send primitives — no InclusionDependency here
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompletionMessage implements Message {
        private static final long serialVersionUID = 1L;
        private ActorRef<DependencyWorker.Message> worker;
        private String columnA;
        private String columnB;
        private boolean holds;
    }

    public static final String DEFAULT_NAME = "dependencyMiner";

    public static final ServiceKey<Message> dependencyMinerService =
            ServiceKey.create(Message.class, DEFAULT_NAME + "Service");

    public static Behavior<Message> create() {
        return Behaviors.setup(DependencyMiner::new);
    }

    private DependencyMiner(ActorContext<Message> context) {
        super(context);

        this.inputFiles = InputConfigurationSingleton.get().getInputFiles();
        this.headerLines = new String[inputFiles.length][];
        this.columnData = new HashMap<>();
        this.inputReaders = new ArrayList<>();
        this.dependencyWorkers = new ArrayList<>();
        this.pendingTasks = new LinkedList<>();

        for (int i = 0; i < inputFiles.length; i++) {
            inputReaders.add(context.spawn(
                    InputReader.create(i, inputFiles[i]),
                    InputReader.DEFAULT_NAME + "_" + i));
        }

        this.resultCollector = context.spawn(ResultCollector.create(), ResultCollector.DEFAULT_NAME);

        this.largeMessageProxy = context.spawn(
                LargeMessageProxy.create(context.getSelf().unsafeUpcast(), false),
                LargeMessageProxy.DEFAULT_NAME);

        context.getSystem().receptionist()
                .tell(Receptionist.register(dependencyMinerService, context.getSelf()));
    }

    // ===== STATE =====

    private final File[] inputFiles;
    private final String[][] headerLines;
    private final List<ActorRef<InputReader.Message>> inputReaders;
    private final List<ActorRef<DependencyWorker.Message>> dependencyWorkers;
    private final ActorRef<ResultCollector.Message> resultCollector;
    private final ActorRef<LargeMessageProxy.Message> largeMessageProxy;

    private final Map<String, Set<String>> columnData;
    private final Queue<DependencyWorker.TaskMessage> pendingTasks;

    private int readersFinished = 0;
    private int totalTasks = 0;
    private int finishedTasks = 0;

    // ===== RECEIVE =====

    @Override
    public Receive<Message> createReceive() {
        return newReceiveBuilder()
                .onMessage(StartMessage.class, this::handle)
                .onMessage(HeaderMessage.class, this::handle)
                .onMessage(BatchMessage.class, this::handle)
                .onMessage(RegistrationMessage.class, this::handle)
                .onMessage(CompletionMessage.class, this::handle)
                .build();
    }

    // ===== HANDLERS =====

    private Behavior<Message> handle(StartMessage msg) {
        for (ActorRef<InputReader.Message> reader : inputReaders) {
            reader.tell(new InputReader.ReadHeaderMessage(getContext().getSelf()));
            reader.tell(new InputReader.ReadBatchMessage(getContext().getSelf(), 10000));
        }
        return this;
    }

    private Behavior<Message> handle(HeaderMessage msg) {
        headerLines[msg.getId()] = msg.getHeader();
        return this;
    }

    private Behavior<Message> handle(BatchMessage msg) {

        if (!msg.getBatch().isEmpty()) {

            for (String[] row : msg.getBatch()) {
                for (int col = 0; col < row.length; col++) {
                    String key = buildKey(msg.getId(), col);
                    columnData.computeIfAbsent(key, k -> new HashSet<>()).add(row[col]);
                }
            }

            inputReaders.get(msg.getId())
                    .tell(new InputReader.ReadBatchMessage(getContext().getSelf(), 10000));

        } else {

            readersFinished++;

            if (readersFinished == inputFiles.length) {
                generateTasks();
                dispatchTasks();
            }
        }

        return this;
    }

    private Behavior<Message> handle(RegistrationMessage msg) {
        if (!dependencyWorkers.contains(msg.getDependencyWorker())) {
            dependencyWorkers.add(msg.getDependencyWorker());
        }
        dispatchTasks();
        return this;
    }

    private Behavior<Message> handle(CompletionMessage msg) {

        finishedTasks++;

        if (msg.isHolds()) {

            String[] partsA = msg.getColumnA().split(":");
            String[] partsB = msg.getColumnB().split(":");

            int fileAId = Integer.parseInt(partsA[0]);
            int colAId  = Integer.parseInt(partsA[1]);

            int fileBId = Integer.parseInt(partsB[0]);
            int colBId  = Integer.parseInt(partsB[1]);

            File fileA = inputFiles[fileAId];
            File fileB = inputFiles[fileBId];

            String attrA = headerLines[fileAId][colAId];
            String attrB = headerLines[fileBId][colBId];

            InclusionDependency ind = new InclusionDependency(
                    fileA,
                    new String[]{attrA},
                    fileB,
                    new String[]{attrB}
            );

            resultCollector.tell(
                    new ResultCollector.ResultMessage(
                            Collections.singletonList(ind)
                    )
            );
        }

        dispatchTasks();

        if (finishedTasks == totalTasks) {
            resultCollector.tell(new ResultCollector.FinalizeMessage());
        }

        return this;
    }

    // ===== TASK GENERATION =====

    private void generateTasks() {

        List<String> columns = new ArrayList<>(columnData.keySet());

        for (String a : columns) {
            for (String b : columns) {
                if (!a.equals(b)) {

                    pendingTasks.add(new DependencyWorker.TaskMessage(
                            largeMessageProxy,
                            a,
                            b,
                            columnData.get(a),
                            columnData.get(b)
                    ));

                    totalTasks++;
                }
            }
        }
    }

    private void dispatchTasks() {
        for (ActorRef<DependencyWorker.Message> worker : dependencyWorkers) {
            if (!pendingTasks.isEmpty()) {
                worker.tell(pendingTasks.poll());
            }
        }
    }

    private String buildKey(int fileId, int columnId) {
        return fileId + ":" + columnId;
    }
}
