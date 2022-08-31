## Notes

Two computations are parallel if they run at the same time
Two computations are concurrent if their lifecycle overlaps

Parallel computations ma not be concurrent
 - independent tasks

Concurrent computations may not be parallel
  - multitasking on the same CPU with resource sharing

## Fibers

Fiber = lightweight thread

Fiber is a description of a computation which runs on one of the threads managed by the ZIO runtime
Fibers are impossible to create manually. Fibers are created through ZIO api create and schedule them automatically on the ZIO runtime

Forking an effect (using method fork) will create another effect which the return value is the fiver on which that effect will be evaluated.

ZIO has a thread pool that manages the execution effects
A fiber has an effect. Fibers are not active like threads, are passive. 

ZIO thread pool has a low number of threads. 
There can be millions of fibers. The only cost is memory. 

Motivation for Fibers:
 - do more need for threads and locks
 - delegate thread management to ZIO runtime
 - avoid synchronization calls with callbacks, like Futures. 
 - maintain pure functional programming
 - keep low level primitives (blocking, waiting, joining, interrupting, cancelling)

ZIO runtime is based on work-stealing work pool
