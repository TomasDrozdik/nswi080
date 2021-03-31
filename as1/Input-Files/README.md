# Assignment 1

[task description url](https://d3s.mff.cuni.cz/legacy/files/teaching/nswi080/labs/Files/task-1-en.html)

---

## Build & Clean

To build run: `./make`

To clean run: `./clean`

## Run

### Server

Server uses default RMI registry port, the solution is not parametrizable by this port as it is hardcoded but in case the default port is blocked try another server or change the `Main.java`, `Server.java` and pass the port to `./run-registry <port>`.

``` shell
./run-registry & # or in separete terminal
./run-server

```
### Client

``` shell
./run-client <options>
```

__Client options__
`./run-client` just passed options to java main method, they are just plain ordered argument with default values.

Following snippet sumarizes in order types, meaning, default values, parsing method, and default

``` java
String  host            = (args.length < 1) ? null : args[0]; // null represents localhost, explicitly pass as "localhost"
long    seed            = (args.length < 2) ? System.currentTimeMillis() : Long.parseLong(args[1]);
long    graphNodes      = (args.length < 3) ? 100 : Integer.parseInt(args[2]);
long    graphEdges      = (args.length < 4) ? 50 : Integer.parseInt(args[3]);
long    transitiveStep  = (args.length < 5) ? 4 : Integer.parseInt(args[4]); // initial value of transitive n, then there are multiples up till transitiveEnd
long transitiveEnd      = (args.length < 6) ? transitiveStep * 4 : Integer.parseInt(args[5]); // inclusive
bool printHeader        = (args.length < 7) ? true : Boolean.parseBoolean(args[6]);
```

---

## Measurements & Plots

Premeasured values used in plots are present in `local.csv` and `remote.csv` for measurements of RMI on localhost and lab.ms.mff.cuni.cz computer respectively.

### Run measurements

In order to run all the measurements and see default values view script `run-measurements`.
The values used in the measurement are:

``` shell
SEED=42
NODES=100
EDGE_BASE=200
EDGE_STEP=50
EDGE_END=500 # inclusive
TRANSITIVE_STEP=4
TRANSITIVE_END=16 # inclusive
```

That is all the measurements are run with random seed 42 on 100 nodes with number of edges starting on 200 going up to 500 by steps of 50.
At the same time each invocation of the main program does transitive version of the distance algorithm.

To run measurements first you have to setup rmiregistry and server then run:
``` shell
./run-measurements <server_url>
```

Where server URL is either `localhost` or some other url in lab e.g. `u-pl4.ms.mff.cuni.cz` where you ran the server.
Output is a csv file, I've put results of localhost server to `local.csv` and results of server on mff lab to `remote.csv`.
These two files are hardcoded in python notebook which does all the plots and explanations.

---

## Results

Run python notebook in `graphs.ipynb` with acompanied explanations.
