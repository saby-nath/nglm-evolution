#########################################
#
#  evolution-launch-preamble.sh
#
#########################################

docker stack deploy -c $DEPLOY_ROOT/stack/stack-gui.yml <_DOCKER_STACK_>-gui

docker stack deploy -c $DEPLOY_ROOT/stack/stack-gui-ssl-monitoring.yml <_DOCKER_STACK_>-gui-ssl-monitoring
