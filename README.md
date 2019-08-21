# Aegean
The source code and scripts for the implementation and evaluation of the Aegean replication framework.

## Implementation Source Code
Aegean source code is under [src directory](https://github.com/GLaDOS-Michigan/Aegean/tree/master/src). 
Two important folder under this directory are [BFT](https://github.com/GLaDOS-Michigan/Aegean/tree/master/src/BFT) 
and [Applications](https://github.com/GLaDOS-Michigan/Aegean/tree/master/src/Applications). 

* `BFT` folder mainly contains replication infrastructure. Here is explanation of some subfolders under `BFT`:

|subfolder name | description
|-------------- |------------
| exec          | implements `executor` which executes request in sequential and parallel mode
| filter        | implements the module which authenticates incoming client requests
| order         | module which orders the client requests before the execution of requests in sequential mode
| verifier      | implements verifier process which decides the resulting state after execution in parallel mode

* `Applications` directory mainly contains experimental applications which runs on top of replication infrastructure. 
For example, `Benchmark` folder contains implementation of microbenchmarks and folder prefixed with `tpcw` contains
implementation of `TPC-W` benchmark such as `tpcw_webserver` (web servers in TPC-W benchmark).

## Evaluation Scripts
Experiments are run with the scripts under [experiments directory](https://github.com/GLaDOS-Michigan/Aegean/tree/master/experiments).
Here are some scripts with their functions:

|script name      | description
|--------------   |------------
| start_client.py | starts requested number of clients which will send requests to middle service for microbenchmarks
| start_sequential_middle.py | starts `executor`, `order` and `filter` nodes to start a replicated middle service in sequential mode for microbenchmarks
| start_sequential_backend.py | starts `executor`, `order` and `filter` nodes to start a replicated backend service in sequential mode for microbenchmarks
| start_middle.py | starts `executor`, `verifier` and `filter` nodes to start a replicated middle service in parallel mode for microbenchmarks
| start_backend.py | starts `executor`, `verifier` and `filter` nodes to start a replicated backend service in parallel mode for microbenchmarks
| start_tpcw_client.py | starts requested number of clients which will send requests to tpc-w web server for TPC-W benchmark
| start_sequential_tpcw_server.py | starts a tpc-w web server in sequential mode which includes starting `executor`, `order` and `filter` nodes
| start_sequential_tpcw_db.py | starts a tpc-w database server in sequential mode which includes starting `executor`, `order` and `filter` nodes
| start_tpcw_server.py | starts a tpc-w web server in parallel mode which includes starting `executor`, `verifier` and `filter` nodes
| start_tpcw_db.py | starts a tpc-w database server in parallel mode which includes starting `executor`, `order` and `filter` node

Other high-level scripts which calls the scripts described above are used to initiate experiments. These high-level scripts
also checks for completion and calculate the throughput. Here are some high level scripts with their usage and explanations:

|script and example usage | effect of example usage
|-------------------------- | ----------------
|./pipelinedSequential_super.py --mode ps 1 4 seqMicro | runs microbenchmarks in pipelined sequential mode and calculates the performance. Number of clients for this experiments starts from 1 and goes until there is no throughput increase or number of clients is greater than 4
|./super_script_tpcw.py --mode p 128 1024 tpcwTest | runs tpc-w benchmark in parallel mode and calculates the performance. Number of clients for this experiments starts from 128 and goes until there is no throughput increase or number of clients is greater than 1024

**Note**: Size of the requests(small, medium,large) for microbenchmarks in `start_client.py` with `REQUEST_SIZE` variable (100, 1000, 10000).

Other than scripts, this directory also contains configuration files for the nodes will be started. 
Following are the list of files and their explanations:

|config file | description
|------------|------------
|clients     | IP or alias of machines where `client` process will be run on
|filters     | IP or alias of machines where middle service `filter` nodes will be run on
|orders      | IP or alias of machines where middle service `order` nodes will be run on for sequential mode
|verifiers   | IP or alias of machines where middle service `verifier` nodes will be run on for parallel mode
|execs       | IP or alias of machines where middle service `executor` nodes will be run
|clients.backend| IP or alias of clients of backend (so should be same IPs with `execs` file)
|filters.backend | IP or alias of machines where backend service `filter` nodes will be run on
|orders.backend | IP or alias of machines where backend service `order` nodes will be run on for sequential mode
|verifiers.backend | IP or alias of machines where backend service `verifier` nodes will be run on for parallel mode
|execs.backend | IP or alias of machines where backend service `executor` nodes will be run on

**Note:** The configuration defined in these files should be converted to `properties` files such as `test.properties` 
and `test.properties.backend` which will be consumed by the processes while starting. The script called`config.sh` can be used to ease this conversion.
This script asks some other input regarding `mode` and `number of tolerated failures` etc. and reads the config files explained above
to create `properties` files which define the setting for Java processes.
