#!/usr/bin/env bash

# first create ClusterWorkRouters cluster
sbt "runMain EShop.lab6.cluster.SeedNode seed-node1" &
sbt "runMain EShop.lab6.cluster.SeedNode seed-node2" &
sbt "runMain EShop.lab6.cluster.SeedNode" & #just the node on random port

# cluster at this point should be up and running

# starting http servers which will also create routers with workers deployed on previously configured cluster
sbt "runMain EShop.lab6.cluster.ClusterProductCatalogServerApp 9001" &
sbt "runMain EShop.lab6.cluster.ClusterProductCatalogServerApp 9002" &
sbt "runMain EShop.lab6.cluster.ClusterProductCatalogServerApp 9003" &


# start gatling tests
#sbt gatling-it:test
#sbt gatling-it:lastReport