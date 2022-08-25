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