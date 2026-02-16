package de.ddm.actors.profiling;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;
import akka.actor.typed.receptionist.Receptionist;
import de.ddm.actors.patterns.LargeMessageProxy;
import de.ddm.serialization.AkkaSerializable;
import lombok.*;

import java.util.Set;

public class DependencyWorker extends AbstractBehavior<DependencyWorker.Message> {

    public interface Message extends AkkaSerializable {}

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReceptionistListingMessage implements Message {
        private static final long serialVersionUID = 1L;
        private Receptionist.Listing listing;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskMessage implements Message {
        private static final long serialVersionUID = 1L;
        private ActorRef<LargeMessageProxy.Message> minerProxy;
        private String columnA;
        private String columnB;
        private Set<String> valuesA;
        private Set<String> valuesB;
    }

    public static final String DEFAULT_NAME = "dependencyWorker";

    public static Behavior<Message> create() {
        return Behaviors.setup(DependencyWorker::new);
    }

    private final ActorRef<LargeMessageProxy.Message> largeMessageProxy;

    private DependencyWorker(ActorContext<Message> context) {
        super(context);

        ActorRef<Receptionist.Listing> adapter =
                context.messageAdapter(Receptionist.Listing.class, ReceptionistListingMessage::new);

        context.getSystem().receptionist()
                .tell(Receptionist.subscribe(DependencyMiner.dependencyMinerService, adapter));

        this.largeMessageProxy =
                context.spawn(LargeMessageProxy.create(context.getSelf().unsafeUpcast(), false),
                        LargeMessageProxy.DEFAULT_NAME);
    }

    @Override
    public Receive<Message> createReceive() {
        return newReceiveBuilder()
                .onMessage(ReceptionistListingMessage.class, this::handle)
                .onMessage(TaskMessage.class, this::handle)
                .build();
    }

    private Behavior<Message> handle(ReceptionistListingMessage msg) {
        for (ActorRef<DependencyMiner.Message> miner :
                msg.getListing().getServiceInstances(DependencyMiner.dependencyMinerService)) {

            miner.tell(new DependencyMiner.RegistrationMessage(getContext().getSelf()));
        }
        return this;
    }

    private Behavior<Message> handle(TaskMessage msg) {

        boolean holds = msg.getValuesB().containsAll(msg.getValuesA());

        DependencyMiner.CompletionMessage completion =
                new DependencyMiner.CompletionMessage(
                        getContext().getSelf(),
                        msg.getColumnA(),
                        msg.getColumnB(),
                        holds
                );

        largeMessageProxy.tell(
                new LargeMessageProxy.SendMessage(
                        completion,
                        msg.getMinerProxy()
                )
        );

        return this;
    }
}
