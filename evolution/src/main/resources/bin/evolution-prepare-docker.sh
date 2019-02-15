#################################################################################
#
#  evolution-prepare-docker.sh
#
#################################################################################

#########################################
#
#  docker pulls
#
#########################################

for SWARM_HOST in $SWARM_HOSTS
do
   echo "evolution-prepare-docker on $SWARM_HOST"
   ssh $SWARM_HOST "
      docker pull ${env.DOCKER_REGISTRY}ev-mysql-gui:${project.name}-${project.version}
      docker pull ${env.DOCKER_REGISTRY}fwk.api:${gui-fwk.version}
      docker pull ${env.DOCKER_REGISTRY}fwkauth.api:${gui-fwk.version}
      docker pull ${env.DOCKER_REGISTRY}fwk.web:${gui-fwk.version}
      docker pull ${env.DOCKER_REGISTRY}csr.api:${gui-csr.version}
      docker pull ${env.DOCKER_REGISTRY}csr.web:${gui-csr.version}
      docker pull ${env.DOCKER_REGISTRY}itm.api:${gui-itm.version}
      docker pull ${env.DOCKER_REGISTRY}itm.web:${gui-itm.version}
      docker pull ${env.DOCKER_REGISTRY}jmr.api:${gui-jmr.version}
      docker pull ${env.DOCKER_REGISTRY}jmr.web:${gui-jmr.version}
      docker pull ${env.DOCKER_REGISTRY}opc.api:${gui-opc.version}
      docker pull ${env.DOCKER_REGISTRY}opc.web:${gui-opc.version}
      docker pull ${env.DOCKER_REGISTRY}iar.api:${gui-iar.version}
      docker pull ${env.DOCKER_REGISTRY}iar.web:${gui-iar.version}
      docker pull ${env.DOCKER_REGISTRY}opr.api:${gui-opr.version}
      docker pull ${env.DOCKER_REGISTRY}opr.web:${gui-opr.version}
      docker pull ${env.DOCKER_REGISTRY}stg.api:${gui-stg.version}
      docker pull ${env.DOCKER_REGISTRY}stg.web:${gui-stg.version}
      docker pull ${env.DOCKER_REGISTRY}sbm.api:${gui-sbm.version}
      docker pull ${env.DOCKER_REGISTRY}sbm.web:${gui-sbm.version}
      docker pull ${env.DOCKER_REGISTRY}ev-e4o-flyway:${flyway.version}
   " &
done

#
#  wait for all pulls to complete
#

wait
echo "evolution-prepare-docker complete"
