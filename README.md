# 📦 DDM-Akka – Distributed Dependency Mining (Akka Typed)

This project is a **distributed, actor-based prototype for inclusion dependency discovery** implemented using **Akka Typed** and **Apache Maven**.  
It demonstrates **actor coordination, message passing, and parallel task execution** over a structured dataset (TPCH).

> ⚠️ **Note**:  
> This implementation focuses on **distributed orchestration and system design**, not on exhaustive dependency verification.  
> The produced dependencies should be interpreted as **candidate inclusion dependencies**.

---

## 📁 Project Structure

```
ddm-akka-main/
│
├── data/
│   └── TPCH/                # Input CSV files (TPCH dataset)
│
├── src/
│   └── main/
│       ├── java/
│       │   └── de/ddm/
│       │       ├── actors/
│       │       │   ├── Master.java
│       │       │   ├── Worker.java
│       │       │   └── profiling/
│       │       │       ├── DependencyMiner.java
│       │       │       ├── DependencyWorker.java
│       │       │       └── InputReader.java
│       │       └── structures/
│       │           └── InclusionDependency.java
│       │
│       └── resources/
│           └── application.conf
│
├── target/
│   └── ddm-akka.jar         # Generated after build
│
├── result.txt               # Output file (generated at runtime)
├── pom.xml                  # Maven configuration
└── README.md                # This file
```

---

## ⚙️ Requirements

### Java
- Java 21 (tested with Eclipse Adoptium)

### Maven
- Apache Maven 3.9.x

---

## 📊 Input Data Setup

The system expects TPCH CSV files in:

```
data/TPCH/
```

⚠️ The program will terminate if this folder does not exist.

---

## 🛠️ Build Instructions

```powershell
& "C:\Program Files\apache-maven-3.9.9\bin\mvn.cmd" clean package --% -Dmaven.test.skip=true

```

---

## ▶️ Run Instructions

```powershell
& "C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot\bin\java.exe" `
-Xmx4g `
-jar target\ddm-akka.jar master
```

---

## 📄 Output

After execution, the system generates:

```
result.txt
```

Each line represents a **candidate inclusion dependency** between relations.

---

## 🧠 Design Highlights

- Akka Typed actors
- Master–Worker architecture
- Asynchronous message passing
- Scalable and fault-isolated design
- Streaming-based input handling

---

## 🧪 Troubleshooting

- Ensure `data/TPCH` exists
- Use full paths for `java` and `mvn`
- Allocate sufficient memory (`-Xmx4g`)

---


