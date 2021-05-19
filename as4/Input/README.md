# Assignment 4 : Hazelcast

## Build and run

First specify hazelcast home in `./setenv.sh`.

Build:
```
./make.sh
```

Run cluster member:
```
./run-member.sh
```

Run clients:
```
./run-client.sh [client_name]
```

Clean:
```
./clean.sh
```

## Design decisions

### Cache
To achieve thread safety in document loading via `ExecutorService` and `Callable` to cache I've used distributed locks provided by `hazelcastInstance.getCPSubsystem.getLock("documentFetch")` lock.

### Non modifying operations
Other non modifying operations are just plain data gets on a particullar map.

### Modifying updates
Other operations that do some modifications use `EntryProcessors`.
I've spent quite some time figuring out why anonymized `EntryProcessors` classes don't work and require a serialization of the `Client` class which was pretty annoying.

### Hazelcast config

The hazelcast config for network is the same as for the example.

__Document cache__
I've set the document cache backup count to 0 so that it does not replicate across members and is just in memory on a single member, the reason is that the contents may be large and I don't expect high interest in a particular docuemnt but rather loads of multiple documents by multiple clients.
To limit the cache I've set the LRU retention policy together with max size.
I wanted to set it to something small to verify it is working but for some backwards compatibility reason it had to be more than 136.

__Metadata map and client profiles__
These fit in memory and it is operated frequently thus we don't want the entries to be serialized since because of the serialization overhead.
