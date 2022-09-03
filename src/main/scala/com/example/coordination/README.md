### Thread coordination

Refs = purely functional, thread safe state management. Purely functional atomic reference.
Promise = waits until another thread unblock it

Promise is a functional bloc on a fiber until you get the signal from another fiber
Waiting on a value which may not yet be available, which cause thread starvation

Mutex = locks a specific area of code