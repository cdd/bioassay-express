# Objective
This document contains information about reliability of MongoDB.

# Background
Older versions of MongoDB had default settings that did not require writes to the database to be successful (fire and forget). The default is now different. To be on the safe side, journaling could be enabled.  This seems to solve the problems on standalone databases.

For replicated databases, write acknowledgement by the majority of the nodes and journaling will ensure security of the data.

# References on this topic:
- [Response of mongoDB CTO to internet rant](https://news.ycombinator.com/item?id=3202959)
- [Stackoverflow discussion](https://stackoverflow.com/questions/10560834/to-what-extent-are-lost-data-criticisms-still-valid-of-mongodb)
- [Jepsen report for version 3.4.0-rc3](https://jepsen.io/analyses/mongodb-3-4-0-rc3)
- [Presentation by mongoDB CTO - How to keep your data safe in mongoDB](https://www.slideshare.net/mongodb/how-to-keep-your-data-safe-in-mongodb)

# Relevant changes in more recent versions:
- **Fire and forget**: In previous version `insert`, `update`, `save`, `remove` operated in a write, but don't acknowledge that it happened. This _default_ was changed in version 2.6; now all writes are acknowledged for success or failure.
- **problems in replication protocol** (see Jepsen test): Problems with the _v1_ replication protocol were fixed in MongoDB 3.4.0. The older _v0_ replication protocol should not be used.

> The v0 protocol (and of course, any test with non-majority writes) continues to lose data in Jepsen tests. With the v1 protocol, majority writes, and linearizable reads, MongoDB 3.4.1 (and the current development release, 3.5.1) pass all MongoDB Jepsen tests: both preserving inserted documents, and linearizable single-document reads, writes, and conditional updates. These results hold during general network partitions, and the isolated & clock-skewed primary scenario outlined above. Future work could explore the impact of node crashes and restarts, and more general partition scenarios.

# Prevention of dataloss
- [MongoDB data loss scenarios and prevention](https://databasevoyager.wordpress.com/2015/06/10/mongodb-data-loss-scenarios-and-prevention/)
- [Write concerns](https://www.percona.com/blog/2016/07/14/mongodb-data-durability/) ([Java API](http://api.mongodb.com/java/current/com/mongodb/WriteConcern.html)): conclusion `{w:"majority", j:1}` is the safest. Default is `{w:1, j:0}`
