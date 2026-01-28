### Notes regarding Request equality and comparators
- Equal requests will not be stored as a single request, instead they will have an equality relation
-A partition will represent all request sets(where each set represents a number of equal requests) that share the same comparator, so the application is divided into multiple partitions based on the comparator used
- A good DB relation schema might be:
1. A RequestEqualitySet entity will have the properties: [id, comparator]
2. A DB table will contain the properties: request_id, requestequalityset_id
- An alternate schema might be represented like:
1. A RequestEquality entity will have the properties: [request_one_id, request_two_id, comparator_id]//NOTE that this might not work, how can we get groups of equal requests? It seems like in this way we have to get request a, then see what requests it equals, then see for each of these what are they equal to.