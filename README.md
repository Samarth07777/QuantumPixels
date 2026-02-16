# Distributed Unary Inclusion Dependency Miner (Akka Typed)

## 1. Project Overview

This project implements **real unary Inclusion Dependency (IND)
discovery** using a distributed, actor-based architecture built with
**Akka Typed**.

The system performs exhaustive value-based inclusion checks across all
attributes of multiple TPCH CSV relations. It preserves the provided
framework architecture while replacing its prototype behavior with a
functional unary IND mining algorithm.

The implementation emphasizes:

-   Distributed execution (master--worker pattern)
-   Streaming-based input handling
-   Deterministic task tracking
-   Clean system termination
-   Clear separation of responsibilities between actors

------------------------------------------------------------------------

## 2. Required Dataset Setup

The TPCH CSV files must be placed inside the following directory:

    data/TPCH/

Expected structure:

    ddm-akka-main/
    │
    ├── data/
    │   └── TPCH/
    │       ├── tpch_customer.csv
    │       ├── tpch_orders.csv
    │       ├── tpch_lineitem.csv
    │       ├── tpch_part.csv
    │       ├── tpch_supplier.csv
    │       ├── tpch_nation.csv
    │       └── tpch_region.csv
    │
    ├── src/
    ├── pom.xml
    └── ...

The system reads input files directly from the filesystem. If
`data/TPCH` does not exist, execution will fail.

------------------------------------------------------------------------

## 3. How to Navigate the Code

All core logic is located under:

    src/main/java/de/ddm/

### 3.1 Bootstrapping

-   `actors/Master.java`\
    Starts the system and spawns the DependencyMiner.

------------------------------------------------------------------------

### 3.2 Core Mining Logic

Located in:

    actors/profiling/

#### DependencyMiner.java

Central coordinator. - Collects column values from streamed batches -
Generates all candidate attribute pairs (A, B) - Distributes tasks to
workers - Tracks task completion deterministically - Reconstructs
InclusionDependency objects - Triggers finalization

#### DependencyWorker.java

Execution unit. - Registers dynamically via Akka Receptionist - Receives
column pair tasks - Checks: A ⊆ B using set containment - Reports
boolean result back to miner

#### InputReader.java

-   Streams CSV files in fixed-size batches
-   Sends headers and tuples to the miner

#### ResultCollector.java

-   Receives confirmed INDs
-   Writes results to `result.txt`
-   Triggers system shutdown during finalization

------------------------------------------------------------------------

## 4. Algorithmic Workflow

1.  Master starts DependencyMiner.
2.  InputReader actors stream CSV files.
3.  Miner aggregates values into `Set<String>` per column.
4.  All ordered attribute pairs (A, B) are generated (A ≠ B).
5.  Workers evaluate: A ⊆ B ⇔ valuesB.containsAll(valuesA)
6.  Miner reconstructs InclusionDependency objects for valid results.
7.  ResultCollector writes discovered INDs.
8.  System terminates when all tasks are completed.

------------------------------------------------------------------------

## 5. Build Instructions (Windows)

    & "C:\Program Files\apache-maven-3.9.9\bin\mvn.cmd" clean package --% -Dmaven.test.skip=true

------------------------------------------------------------------------

## 6. Run Instructions (Windows)

    & "C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot\bin\java.exe" `
    -Xmx6g `
    -jar target\ddm-akka.jar master

------------------------------------------------------------------------

## 7. Output

After successful execution, the system generates:

    result.txt

Each line represents a discovered unary inclusion dependency.

------------------------------------------------------------------------

## 8. Key Implementation Characteristics

-   Real value-based unary IND detection
-   No random dependency generation
-   No artificial workload simulation
-   No time-based termination
-   Deterministic task accounting
-   Distributed master--worker execution
-   Safe actor communication (primitive message exchange)

------------------------------------------------------------------------

## 9. Evaluation Notes

This system performs exhaustive unary inclusion dependency discovery.
Because no semantic pruning is applied, the output may include valid
inclusion relationships that are not foreign keys but satisfy the formal
containment definition.

The architecture remains intact while the mining logic has been fully
implemented.

------------------------------------------------------------------------

## 10. Conclusion

The project demonstrates how unary inclusion dependency discovery can be
realized as a distributed actor-based workflow using Akka Typed.

By separating input streaming, coordination, execution, and result
management, the system achieves clarity, scalability, and correctness
while preserving the original framework structure.
