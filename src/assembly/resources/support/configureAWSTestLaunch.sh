#########################################
#
#  configureLaunch.sh
#
#########################################

#
#  maven
#

export MAVEN_REPOSITORY=${MAVEN_REPOSITORY:-$HOME/.m2/repository}

#
#  deploy root
#

GITVER=`java -jar $MAVEN_REPOSITORY/com/evolving/jgitver-cli/1.0.0/jgitver-cli-1.0.0.jar`
export DEPLOY_PACKAGE=nglm-evolution-${GITVER}.tgz
export DEPLOY_PACKAGE_BASENAME="${DEPLOY_PACKAGE%.*}"
export DEPLOY_ROOT=$(readlink -f "..")/build/${DEPLOY_PACKAGE_BASENAME}

#
#  docker stack
#

export DOCKER_STACK=ev

#
#  docker user/group
#

export DOCKER_USER=evol
export DOCKER_RESTART_POLICY=none

#
#  docker pull
#

export DOCKER_PULL=true

#
#  timezone
#

export DEPLOYMENT_TIMEZONE=Europe/Paris
export DEPLOYMENT_ISO8601_TIMEZONE=+0100
export DEPLOYMENT_LOCALE_LANGUAGE=en
export DEPLOYMENT_LOCALE_COUNTRY=US

#
#  elasticsearch
#

export ELASTICSEARCH_CLUSTER_NAME=nglm-cluster

#
#  filesystem locations
#

export NGLM_ROOT=/mnt/disk1/nglm
export NGLM_CORE_RUNTIME=$NGLM_ROOT/nglm-core-runtime
export NGLM_KAFKA_RUNTIME=$NGLM_ROOT/nglm-kafka-runtime
export NGLM_ES_RUNTIME=$NGLM_ROOT/nglm-es-runtime
export NGLM_REDIS_RUNTIME=$NGLM_ROOT/nglm-redis-runtime
export NGLM_STREAMS_RUNTIME=$NGLM_ROOT/nglm-streams-runtime
export NGLM_DATA=$NGLM_ROOT/data
export NGLM_SUBSCRIBERGROUP_DATA=$NGLM_DATA/subscribergroup

#
#  startup
#

export STARTUP_DEPENDENCY_TIMEOUT=120

#
#  debug
#

export EVOLUTION_ENGINE_DEBUG=

#
#  swarm
#

export KAFKA1_HOST=kafka1
export KAFKA2_HOST=kafka2
export KAFKA3_HOST=kafka3
export REDIS_HOST=redis
export SWARM_HOSTS="$KAFKA1_HOST $KAFKA2_HOST $KAFKA3_HOST $REDIS_HOST"

#
#  swarm (ip addresses)
#

export KAFKA1_HOST_IP=172.31.41.172
export KAFKA2_HOST_IP=172.31.39.68
export KAFKA3_HOST_IP=172.31.32.98
export REDIS_HOST_IP=172.31.40.63

#
#  nglm configuration
#

export PRODUCTION_BUILD=false
export ZOOKEEPER_CONFIGURATION="1:001:${KAFKA1_HOST}:2181:2182:2183:2301 2:002:$KAFKA2_HOST:2181:2182:2183:2301 3:003:$KAFKA3_HOST:2181:2182:2183:2301"
export BROKER_CONFIGURATION="1:001:${KAFKA1_HOST}:9092:7071:9999 2:002:${KAFKA2_HOST}:9092:7071:9999 3:003:${KAFKA3_HOST}:9092:7071:9999"
export CONNECT_CONFIGURATION="001:${KAFKA1_HOST}:8091:7061"
export REGISTRY_CONFIGURATION="001:${KAFKA1_HOST}:8081 002:${KAFKA2_HOST}:8081 003:${KAFKA3_HOST}:8081"
export KAFKAMONITOR_CONFIGURATION="001:${KAFKA1_HOST}:9801:9802"
export KAFKAMANAGER_CONFIGURATION="001:${KAFKA1_HOST}:9000"
export BURROW_CONFIGURATION="001:${KAFKA1_HOST}:9811:9812"
export CADVISOR_CONFIGURATION="001:${KAFKA1_HOST}:9701"
export ELASTICSEARCH_CONFIGURATION="1:001:${KAFKA2_HOST}:9200:9300 2:002:${KAFKA3_HOST}:9200:9300"
export ESROUTER_CONFIGURATION="1:001:${KAFKA1_HOST}:9200:9300"
export REDIS_CONFIGURATION_SUBSCRIBERPROFILE="001:${REDIS_HOST}:${REDIS_HOST_IP}:6389 002:${REDIS_HOST}:${REDIS_HOST_IP}:6390"
export REDIS_CONFIGURATION_SUBSCRIBERIDS="001:${REDIS_HOST}:${REDIS_HOST_IP}:6369 002:${REDIS_HOST}:${REDIS_HOST_IP}:6370"
export REDISSENTINEL_CONFIGURATION="001:${KAFKA1_HOST}:6451 002:${KAFKA2_HOST}:6451 003:${KAFKA3_HOST}:6451"
export SIMPLETRANSFORM_CONFIGURATION=
export EVOLUTIONENGINE_CONFIGURATION="001:${KAFKA2_HOST}:9601:7373"
export SUBSCRIBERMANAGER_CONFIGURATION="001:${KAFKA3_HOST}:9621"
export LICENSEMANAGER_CONFIGURATION="001:${KAFKA1_HOST}:7443:7051 002:${KAFKA2_HOST}:7443:7051 003:${KAFKA3_HOST}:7443:7051"
export NGLM_EXPORTER_CONFIGURATION="001:${KAFKA_HOST}:7450:/evol/nglm/data/externalaggregates,$NGLM_DATA/externalaggregates;/evol/nglm/data/subscribergroup,$NGLM_DATA/subscribergroup"

#
#  license (uuencoded license)
#

export NGLM_LICENSE="AQa4WBvdmCZkeciSLLK+m2odTfRhj+CPQuJYOIqXxRkUEgF6499OJq3dxR2WQ/dTKMoFpwuQRRWjuP19OybbpnI1KD/OFi1dM4aap2A40HztoMFKdLixp+M6UKi/WIe22WVEQ6hVdK4HCD3Mf3ptnlK4GPTUWJWOVpY9cdBuhRKCtNR+/ppM9QOgvlzPqEKKpem3R0QXavRW/5NCpu2K9QvIZrzKq5RWx/ltWvIueDHFvG/H4Z9337ptqLl6I2qYuo41nRNcLkR+D7mYbx9XoV5YNSyvSPBtfe6TSfOcl4JaKiGBn8G2pJ2RpA9rVQ706p4aa0B14OjW2b6I1dNDgISrMH2yCxQDzNOLVgZ/2sdMm50BMgHvCK1LlqC8gl2UmDhP1pZlQXRj8O183EKlfKRI1PyYk50hAcLV2EKPVFYWEmzDXS87gxahYPwnwvyTTjtP7yKbiXZEYdsjG4ECE0H1IlyrnnieCaHx6sXOoTZF+95P2W9p7tHz9lo3Wl+u2P5vLw7FerznVxfTg0WS2rqbrFDRC995SmHRzjcihvu5nex3EWJ3HTexEGadr7BsqSQYaVWAHrm7nerWcB4jjNo03U8UjgBha07q4Goe7TSbyNrOUOp3iEFJVZUt/byc/fzz0CteZwep+CavTN9AGGtolw6Wr2qWjZQlT/0jcdGk2LBqWCV6qgelA0ANGQW9J8KtJYmq+EDTo5iR1a19eW52g2MR4QIVtgWeeJC3BAMqrRHh1yVQgMsdFgOE1V8YNtNFPMFYDLyjhIY1OhW4LIJItglZDy1cNC9p0hjk+nViXPgowvU+9qz3Cz/ECfmwNA=="

#
#  gui manager api
#

export GUIMANAGER_HOST=${KAFKA1_HOST}
export GUIMANAGER_PORT=7081
export GUIMANAGER_MONITORING_PORT=7082

#
#  criteria api
#

export CRITERIAAPI_HOST=${KAFKA1_HOST}
export CRITERIAAPI_PORT=7084
export CRITERIAAPI_MONITORING_PORT=7085

#
#  heap opts
#

export REGISTRY_MEMORY="512m"
export ELASTICSEARCH_MEMORY="1g"
export ESROUTER_MEMORY="1g"
export ZOOKEEPER_MEMORY="512m"
export BROKER_MEMORY="2g"
export CONNECT_MEMORY="1g"
export SIMPLETRANSFORM_MEMORY="512m"
export GUIMANAGER_MEMORY="512m"
export CRITERIAAPI_MEMORY="256m"
export EVOLUTIONENGINE_MEMORY="512m"
export SUBSCRIBERMANAGER_MEMORY="512m"
export SUBSCRIBERGROUP_MEMORY="256m"
export LICENSEMANAGER_MEMORY="256m"

#
#  kafka
#

export KAFKA_REPLICATION_FACTOR=1
export KAFKA_STREAMS_STANDBY_REPLICAS=1
export SUBSCRIBER_PARTITIONS=6
export FILECONNECTOR_PARTITIONS_LARGE=1
export FILECONNECTOR_PARTITIONS_SMALL=1

#
#  monitoring
#

export MONITORING_HOST=${KAFKA1_HOST}
export PROMETHEUS_CORE_PORT=9831
export PROMETHEUS_ENVIRONMENT_PORT=9832
export PROMETHEUS_APPLICATION_PORT=9830
export NODE_EXPORTER_PORT=9101
export ALERTMANAGER_PORT=9833
export ALERTMANAGER_HTTP_PROXY=
export UNSEE_PORT=9834
export GRAFANA_PORT=3000
export GRAFANA_USER=admin
export GRAFANA_PASSWORD=admin
export ELASTICSEARCHEXPORTER_PORT=9108
export REDIS_EXPORTER_PORT=9109
export KIBANA_PORT=9835
export LOGSTASH_API_PORT=9836
export LOGSTASH_INPUT_PORT=9837
export LOGSPOUT_PORT=9838
export LANDING_PAGE_PORT=9110

#
#  slack
#

export SLACK_USERNAME=
export SLACK_CHANNEL=
export SLACK_URL=

#
#  trace level
#

export TRACE_LEVEL=INFO
