## How to run
[instruction](https://developer.lightbend.com/guides/akka-distributed-workers-scala/experimenting.html)

<pre>
    +------------------------------------------+       +--------------------------+                    
    |            FrontEnd Producer             |       |    FrontEnd Consumer     |                    
    |--------------------|---------------------|       |                          |                    
    |Idle                |Busy                 |       |                          |                    
    |               Switch state and send 'Work'       | Clients that subscribed  |                    
    |Produce a 'Work' -----------------------+ |       | one or more topics, such |                    
    |for every 'Tick'    |  ACK     NotOK    | |       | as "WorkResult".         |                    
    |scheduled.          |   ^         ^     | |       |                          |                    
    +------------------------------------------+       +--------------------------+                    
                             |Schedule |     |                       ^                                 
                             |'ReTry'  |     |                       | WorkRst                         
                             |until    |     |         +--------------------------+                    
                             |ACK      |     |         |   Akka PubSub Mediator   |                    
                             |         |     |         +--------------------------+                    
                             |         |     |                       ^                                 
                         Otherwise Exception v                       | WorkRst     
   +-------------------------|----------------------------------------------------+
   +---------++---------++-----------+             Master            +-----------+|                    
   |         ||persist  || persist   |maintains:                     | Recovery  ||                    
   |Register ||WorkStart|| WorkAccept|1. PubSub topics  +------------+           ||                    
   |new      ||event    || event     |2. workers' states|Persist     |------------|                    
   |worker   ||         ||           |and their HP      |WorkerFailed|           ||                    
   |or       ||update   || update    |3. states of each |event       |from       ||                    
   |replenish||WorkerState wokerState|work /to be done  |            |'event-log-||                    
   |worker's ||WorkState|| workState |	/completed      |update      |replay'    ||                    
   |HP       ||         |+-----------+    /in progress  |wokerState  |           ||                    
   +---------++---------+|persist work  ----------------+workState   |           ||                    
   | ^     |      ^  |   |completed   |Master schedules |------------+from 'snap-||                    
   | |     |      |  |   |event      ||'cleanupTick' to |       ^    |shot'      ||                    
   +-|-----|------|--|---|^----------||-----------------|---^---|----+-----------++                    
     |     |      |  |   || update   ||be sent to itself|   |   |                                      
     |'has-work'  |'Work'|| 2 states ||to check and prune   |   | Worker sends                         
     |init |      |  |   || publish  ||time-out workers ----+   | 'De-register'                        
     |work |      |  |   || WorkRst  |+-----------------+       | before gracefully                    
     |delegation  |  |   +-----------|+             Worker sends| quit cluster                         
     |     v      |  |'Done'         |              'WorkFail'  |                                      
   +-|------------|--|----|-+ +------|-----------------+ +------|-----------------+                    
   | |      Worker|  |    | | |  Ack | Worker          | |      |  Worker         |                    
   | |          | |  |    | | |      v                 | |      ^                 |                    
   |heart       | |  |    | | |   -----------------+   | |      |--------         |                    
   |beat   -----|-+  v    | | |switch              |   | |  ----+     Worker      |                    
   |       req  |switch to| | |to                  v   | |In case of  de-register |                    
   |       for  |working  | | |waitAck    Go back to   | |any other   could happen|                    
   |       work |state    | | |state      idle state   | |Exceptions  during any  |                    
   |       if   |    |    | | |           upon Ack     | |occurred    state       |                    
   |       idle |    | +---------->       arrival      | |                        |                    
   +------------|----|-|----+ +------------------------+ +------------------------+                    
                     | |             |    Ready to accept   Each Worker init|                          
            send     | |             |    'has-work'        an Executor and |                          
            DoWork   | |Done         |    signal from       coordinate work |                          
            to       | |             |    Master            between Master  |                          
            executor | |             |                      and Executor    |                          
                     v |             v                                      v                          
             +-----------+    +-----------+ Executor is where   +-----------+                          
             | Executor  |    | Executor  | actual computation  | Executor  |                          
             +-----------+    +-----------+ carries out         +-----------+                          
</pre>
                                                                                                       
