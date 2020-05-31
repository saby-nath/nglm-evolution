#################################################################################
#
#  evolution-prepare-stack-environment.sh
#
#################################################################################

#########################################
#
#  service configuration
#
#########################################

#
#  evolutionengine -- configuration
#

EVOLUTIONENGINE_CONFIGURATION=`echo $EVOLUTIONENGINE_CONFIGURATION | sed 's/ /\n/g' | uniq`
EVOLUTIONENGINE_SUBSCRIBERPROFILE_ENDPOINTS=
EVOLUTIONENGINE_SUBSCRIBERPROFILE_ENDPOINT=
EVOLUTIONENGINE_PROMETHEUS=
for TUPLE in $EVOLUTIONENGINE_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export SUBSCRIBERPROFILE_PORT=`echo $TUPLE | cut -d: -f3`
   export INTERNAL_PORT=`echo $TUPLE | cut -d: -f4`
   export MONITORING_PORT=`echo $TUPLE | cut -d: -f5`
   export DEBUG_PORT=`echo $TUPLE | cut -d: -f6`
   if [ -n "$EVOLUTIONENGINE_SUBSCRIBERPROFILE_ENDPOINTS" ]; then
     EVOLUTIONENGINE_SUBSCRIBERPROFILE_ENDPOINTS="$EVOLUTIONENGINE_SUBSCRIBERPROFILE_ENDPOINTS,$HOST:$SUBSCRIBERPROFILE_PORT"
   else
     EVOLUTIONENGINE_SUBSCRIBERPROFILE_ENDPOINTS="$HOST:$SUBSCRIBERPROFILE_PORT"
   fi
   if [ -z "$EVOLUTIONENGINE_SUBSCRIBERPROFILE_ENDPOINT" ]; then
     EVOLUTIONENGINE_SUBSCRIBERPROFILE_ENDPOINT="$HOST:$SUBSCRIBERPROFILE_PORT"
   fi
   if [ -n "$EVOLUTIONENGINE_PROMETHEUS" ]; then
     EVOLUTIONENGINE_PROMETHEUS="$EVOLUTIONENGINE_PROMETHEUS,'$HOST:$MONITORING_PORT'"
   else
     EVOLUTIONENGINE_PROMETHEUS="'$HOST:$MONITORING_PORT'"
   fi
done
export EVOLUTIONENGINE_SUBSCRIBERPROFILE_ENDPOINT
export EVOLUTIONENGINE_SUBSCRIBERPROFILE_ENDPOINTS
export EVOLUTIONENGINE_PROMETHEUS

#
#  journeytrafficengine -- configuration
#

JOURNEYTRAFFICENGINE_CONFIGURATION=`echo $JOURNEYTRAFFICENGINE_CONFIGURATION | sed 's/ /\n/g' | uniq`
JOURNEYTRAFFICENGINE_PROMETHEUS=
for TUPLE in $JOURNEYTRAFFICENGINE_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export MONITORING_PORT=`echo $TUPLE | cut -d: -f3`
   export DEBUG_PORT=`echo $TUPLE | cut -d: -f4`
   if [ -n "$JOURNEYTRAFFICENGINE_PROMETHEUS" ]; then
     JOURNEYTRAFFICENGINE_PROMETHEUS="$JOURNEYTRAFFICENGINE_PROMETHEUS,'$HOST:$MONITORING_PORT'"
   else
     JOURNEYTRAFFICENGINE_PROMETHEUS="'$HOST:$MONITORING_PORT'"
   fi
done
export JOURNEYTRAFFICENGINE_PROMETHEUS

#
#  ucgengine -- configuration
#

UCGENGINE_CONFIGURATION=`echo $UCGENGINE_CONFIGURATION | sed 's/ /\n/g' | uniq`
UCGENGINE_PROMETHEUS=
UCGENGINE_ENABLED=false
for TUPLE in $UCGENGINE_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export MONITORING_PORT=`echo $TUPLE | cut -d: -f3`
   if [ -n "$UCGENGINE_PROMETHEUS" ]; then
     UCGENGINE_PROMETHEUS="$UCGENGINE_PROMETHEUS,'$HOST:$MONITORING_PORT'"
   else
     UCGENGINE_PROMETHEUS="'$HOST:$MONITORING_PORT'"
   fi
   UCGENGINE_ENABLED=true
done
export UCGENGINE_PROMETHEUS
export UCGENGINE_ENABLED

#
#  INFULFILLMENTMANAGER -- configuration
#

INFULFILLMENTMANAGER_CONFIGURATION=`echo $INFULFILLMENTMANAGER_CONFIGURATION | sed 's/ /\n/g' | uniq`
INFULFILLMENTMANAGER_PROMETHEUS=
INFULFILLMENTMANAGER_ENABLED=false
for TUPLE in $INFULFILLMENTMANAGER_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export MONITORING_PORT=`echo $TUPLE | cut -d: -f3`
   export DEBUG_PORT=`echo $TUPLE | cut -d: -f4`
   export PLUGIN_NAME=`echo $TUPLE | cut -d: -f5`
   export PLUGIN_CONFIGURATION=`echo $TUPLE | cut -d: -f6-`
   if [ -n "$INFULFILLMENTMANAGER_PROMETHEUS" ]; then
     INFULFILLMENTMANAGER_PROMETHEUS="$INFULFILLMENTMANAGER_PROMETHEUS,'$HOST:$MONITORING_PORT'"
   else
     INFULFILLMENTMANAGER_PROMETHEUS="'$HOST:$MONITORING_PORT'"
   fi
  INFULFILLMENTMANAGER_ENABLED=true
done
export INFULFILLMENTMANAGER_PROMETHEUS
export INFULFILLMENTMANAGER_ENABLED

#
#  EMPTYFULFILLMENTMANAGER -- configuration
#

EMPTYFULFILLMENTMANAGER_CONFIGURATION=`echo $EMPTYFULFILLMENTMANAGER_CONFIGURATION | sed 's/ /\n/g' | uniq`
EMPTYFULFILLMENTMANAGER_PROMETHEUS=
EMPTYFULFILLMENTMANAGER_ENABLED=false
for TUPLE in $EMPTYFULFILLMENTMANAGER_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export MONITORING_PORT=`echo $TUPLE | cut -d: -f3`
   export DEBUG_PORT=`echo $TUPLE | cut -d: -f4`
   export PLUGIN_NAME=`echo $TUPLE | cut -d: -f5`
   if [ -n "$EMPTYFULFILLMENTMANAGER_PROMETHEUS" ]; then
     EMPTYFULFILLMENTMANAGER_PROMETHEUS="$EMPTYFULFILLMENTMANAGER_PROMETHEUS,'$HOST:$MONITORING_PORT'"
   else
     EMPTYFULFILLMENTMANAGER_PROMETHEUS="'$HOST:$MONITORING_PORT'"
   fi
  EMPTYFULFILLMENTMANAGER_ENABLED=true
done
export EMPTYFULFILLMENTMANAGER_PROMETHEUS
export EMPTYFULFILLMENTMANAGER_ENABLED

#
#  COMMODITYDELIVERYMANAGER -- configuration
#

COMMODITYDELIVERYMANAGER_CONFIGURATION=`echo $COMMODITYDELIVERYMANAGER_CONFIGURATION | sed 's/ /\n/g' | uniq`
COMMODITYDELIVERYMANAGER_PROMETHEUS=
COMMODITYDELIVERYMANAGER_ENABLED=false
for TUPLE in $COMMODITYDELIVERYMANAGER_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export MONITORING_PORT=`echo $TUPLE | cut -d: -f3`
   export DEBUG_PORT=`echo $TUPLE | cut -d: -f4`
   if [ -n "$COMMODITYDELIVERYMANAGER_PROMETHEUS" ]; then
     COMMODITYDELIVERYMANAGER_PROMETHEUS="$COMMODITYDELIVERYMANAGER_PROMETHEUS,'$HOST:$MONITORING_PORT'"
   else
     COMMODITYDELIVERYMANAGER_PROMETHEUS="'$HOST:$MONITORING_PORT'"
   fi
  COMMODITYDELIVERYMANAGER_ENABLED=true
done
export COMMODITYDELIVERYMANAGER_PROMETHEUS
export COMMODITYDELIVERYMANAGER_ENABLED

#
#  PURCHASEFULFILLMENTMANAGER -- configuration
#

PURCHASEFULFILLMENTMANAGER_CONFIGURATION=`echo $PURCHASEFULFILLMENTMANAGER_CONFIGURATION | sed 's/ /\n/g' | uniq`
PURCHASEFULFILLMENTMANAGER_PROMETHEUS=
PURCHASEFULFILLMENTMANAGER_ENABLED=false
for TUPLE in $PURCHASEFULFILLMENTMANAGER_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export MONITORING_PORT=`echo $TUPLE | cut -d: -f3`
   export DEBUG_PORT=`echo $TUPLE | cut -d: -f4`
   if [ -n "$PURCHASEFULFILLMENTMANAGER_PROMETHEUS" ]; then
     PURCHASEFULFILLMENTMANAGER_PROMETHEUS="$PURCHASEFULFILLMENTMANAGER_PROMETHEUS,'$HOST:$MONITORING_PORT'"
   else
     PURCHASEFULFILLMENTMANAGER_PROMETHEUS="'$HOST:$MONITORING_PORT'"
   fi
  PURCHASEFULFILLMENTMANAGER_ENABLED=true
done
export PURCHASEFULFILLMENTMANAGER_PROMETHEUS
export PURCHASEFULFILLMENTMANAGER_ENABLED

#
#  notificationmanagersms -- configuration
#

NOTIFICATIONMANAGER_SMS_CONFIGURATION=`echo $NOTIFICATIONMANAGER_SMS_CONFIGURATION | sed 's/ /\n/g' | uniq`
NOTIFICATIONMANAGER_SMS_PROMETHEUS=
NOTIFICATIONMANAGER_SMS_ENABLED=false
for TUPLE in $NOTIFICATIONMANAGER_SMS_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export MONITORING_PORT=`echo $TUPLE | cut -d: -f3`
   if [ -n "$NOTIFICATIONMANAGER_SMS_PROMETHEUS" ]; then
     NOTIFICATIONMANAGER_SMS_PROMETHEUS="$NOTIFICATIONMANAGER_SMS_PROMETHEUS,'$HOST:$MONITORING_PORT'"
   else
     NOTIFICATIONMANAGER_SMS_PROMETHEUS="'$HOST:$MONITORING_PORT'"
   fi
  NOTIFICATIONMANAGER_SMS_ENABLED=true
done
export NOTIFICATIONMANAGER_SMS_PROMETHEUS
export NOTIFICATIONMANAGER_SMS_ENABLED

#
#  notificationmanagermail -- configuration
#

NOTIFICATIONMANAGER_MAIL_CONFIGURATION=`echo $NOTIFICATIONMANAGER_MAIL_CONFIGURATION | sed 's/ /\n/g' | uniq`
NOTIFICATIONMANAGER_MAIL_PROMETHEUS=
NOTIFICATIONMANAGER_MAIL_ENABLED=false
for TUPLE in $NOTIFICATIONMANAGER_MAIL_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export MONITORING_PORT=`echo $TUPLE | cut -d: -f3`
   if [ -n "$NOTIFICATIONMANAGER_MAIL_PROMETHEUS" ]; then
     NOTIFICATIONMANAGER_MAIL_PROMETHEUS="$NOTIFICATIONMANAGER_MAIL_PROMETHEUS,'$HOST:$MONITORING_PORT'"
   else
     NOTIFICATIONMANAGER_MAIL_PROMETHEUS="'$HOST:$MONITORING_PORT'"
   fi
  NOTIFICATIONMANAGER_MAIL_ENABLED=true
done
export NOTIFICATIONMANAGER_MAIL_PROMETHEUS
export NOTIFICATIONMANAGER_MAIL_ENABLED

#
#  notificationmanagerpush -- configuration
#

NOTIFICATIONMANAGER_PUSH_CONFIGURATION=`echo $NOTIFICATIONMANAGER_PUSH_CONFIGURATION | sed 's/ /\n/g' | uniq`
NOTIFICATIONMANAGER_PUSH_PROMETHEUS=
NOTIFICATIONMANAGER_PUSH_ENABLED=false
for TUPLE in $NOTIFICATIONMANAGER_PUSH_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export MONITORING_PORT=`echo $TUPLE | cut -d: -f3`
   if [ -n "$NOTIFICATIONMANAGER_PUSH_PROMETHEUS" ]; then
     NOTIFICATIONMANAGER_PUSH_PROMETHEUS="$NOTIFICATIONMANAGER_PUSH_PROMETHEUS,'$HOST:$MONITORING_PORT'"
   else
     NOTIFICATIONMANAGER_PUSH_PROMETHEUS="'$HOST:$MONITORING_PORT'"
   fi
  NOTIFICATIONMANAGER_PUSH_ENABLED=true
done
export NOTIFICATIONMANAGER_PUSH_PROMETHEUS
export NOTIFICATIONMANAGER_PUSH_ENABLED

#
#  notificationmanager -- configuration
#

NOTIFICATIONMANAGER_CONFIGURATION=`echo $NOTIFICATIONMANAGER_CONFIGURATION | sed 's/ /\n/g' | uniq`
NOTIFICATIONMANAGER_PROMETHEUS=
NOTIFICATIONMANAGER_ENABLED=false
for TUPLE in $NOTIFICATIONMANAGER_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1` 
   export HOST=`echo $TUPLE | cut -d: -f2` 
   export MONITORING_PORT=`echo $TUPLE | cut -d: -f3` 
   if [ -n "$NOTIFICATIONMANAGER_PROMETHEUS" ]; then 
     NOTIFICATIONMANAGER_PROMETHEUS="$NOTIFICATIONMANAGER_PROMETHEUS,'$HOST:$MONITORING_PORT'"
   else 
     NOTIFICATIONMANAGER_PROMETHEUS="'$HOST:$MONITORING_PORT'"
   fi   
  NOTIFICATIONMANAGER_ENABLED=true
done
export NOTIFICATIONMANAGER_PROMETHEUS
export NOTIFICATIONMANAGER_ENABLED



#
#  reportmanager -- configuration
#

REPORTMANAGER_CONFIGURATION=`echo $REPORTMANAGER_CONFIGURATION | sed 's/ /\n/g' | uniq`
REPORTMANAGER_PROMETHEUS=
REPORTMANAGER_ENABLED=false
for TUPLE in $REPORTMANAGER_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export MONITORING_PORT=`echo $TUPLE | cut -d: -f3`
   export DEBUG_PORT=`echo $TUPLE | cut -d: -f4`
   if [ -n "$REPORTMANAGER_PROMETHEUS" ]; then
     REPORTMANAGER_PROMETHEUS="$REPORTMANAGER_PROMETHEUS,'$HOST:$MONITORING_PORT'"
   else
     REPORTMANAGER_PROMETHEUS="'$HOST:$MONITORING_PORT'"
   fi
  REPORTMANAGER_ENABLED=true
done
export REPORTMANAGER_PROMETHEUS
export REPORTMANAGER_ENABLED

#
#  reportscheduler -- configuration
#

REPORTSCHEDULER_CONFIGURATION=`echo $REPORTSCHEDULER_CONFIGURATION | sed 's/ /\n/g' | uniq`
REPORTSCHEDULER_PROMETHEUS=
REPORTSCHEDULER_ENABLED=false
for TUPLE in $REPORTSCHEDULER_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export MONITORING_PORT=`echo $TUPLE | cut -d: -f3`
   export DEBUG_PORT=`echo $TUPLE | cut -d: -f4`
   if [ -n "$REPORTSCHEDULER_PROMETHEUS" ]; then
     REPORTSCHEDULER_PROMETHEUS="$REPORTSCHEDULER_PROMETHEUS,'$HOST:$MONITORING_PORT'"
   else
     REPORTSCHEDULER_PROMETHEUS="'$HOST:$MONITORING_PORT'"
   fi
  REPORTSCHEDULER_ENABLED=true
done
export REPORTSCHEDULER_PROMETHEUS
export REPORTSCHEDULER_ENABLED

#
#  datacubemanager -- configuration
#

export DATACUBEMANAGER_ENABLED=true
export DATACUBEMANAGER_PROMETHEUS="'$DATACUBEMANAGER_HOST:$DATACUBEMANAGER_MONITORING_PORT'"

#
#  mysql-gui -- configuration
#

MYSQL_GUI_SERVER_HOST=
MYSQL_GUI_SERVER_HOST_IP=
MYSQL_GUI_SERVER_PORT=
for TUPLE in $MYSQL_GUI_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export PORT=`echo $TUPLE | cut -d: -f4`
   if [ -z "$MYSQL_GUI_SERVER_HOST" ]; then
     MYSQL_GUI_SERVER_HOST="$HOST"
     MYSQL_GUI_SERVER_HOST_IP="$HOST_IP"
     MYSQL_GUI_SERVER_PORT="$PORT"
   fi
done
export MYSQL_GUI_SERVER_HOST
export MYSQL_GUI_SERVER_HOST_IP
export MYSQL_GUI_SERVER_PORT

#
#  subscriberids redis -- configuration
#

SUBSCRIBERIDS_REDIS_SERVER=
SUBSCRIBERIDS_REDIS_SERVER_HOST=
SUBSCRIBERIDS_REDIS_SERVER_PORT=
SUBSCRIBERIDS_REDIS_CONNECTION_STRING=
for TUPLE in $REDIS_CONFIGURATION_SUBSCRIBERIDS
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export PORT=`echo $TUPLE | cut -d: -f4`
   if [ -z "$SUBSCRIBERIDS_REDIS_SERVER" ]; then
     SUBSCRIBERIDS_REDIS_SERVER="$HOST:$PORT"
     SUBSCRIBERIDS_REDIS_SERVER_HOST="$HOST"
     SUBSCRIBERIDS_REDIS_SERVER_PORT="$PORT"
     SUBSCRIBERIDS_REDIS_CONNECTION_STRING="$HOST:$PORT"
   else
     SUBSCRIBERIDS_REDIS_CONNECTION_STRING+=",$HOST:$PORT"
   fi
done
export SUBSCRIBERIDS_REDIS_SERVER
export SUBSCRIBERIDS_REDIS_SERVER_HOST
export SUBSCRIBERIDS_REDIS_SERVER_PORT
export SUBSCRIBERIDS_REDIS_CONNECTION_STRING="$SUBSCRIBERIDS_REDIS_CONNECTION_STRING,allowAdmin=true,syncTimeout=5000"

#
#  GUI FWK WEB -- configuration
#

GUI_FWK_WEB_SERVER=
GUI_FWK_WEB_SERVER_HOST=
GUI_FWK_WEB_SERVER_HOST_IP=
GUI_FWK_WEB_SERVER_HOST_EXTERNAL_IP=
GUI_FWK_WEB_SERVER_PORT=
for TUPLE in $GUI_FWK_WEB_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   if [ -z "$GUI_FWK_WEB_SERVER" ]; then
     GUI_FWK_WEB_SERVER="$HOST:$PORT"
     GUI_FWK_WEB_SERVER_HOST="$HOST"
     GUI_FWK_WEB_SERVER_HOST_IP="$HOST_IP"
     GUI_FWK_WEB_SERVER_HOST_EXTERNAL_IP="$HOST_EXTERNAL_IP"
     GUI_FWK_WEB_SERVER_PORT="$PORT"
   fi
done
export GUI_FWK_WEB_SERVER
export GUI_FWK_WEB_SERVER_HOST
export GUI_FWK_WEB_SERVER_HOST_IP
export GUI_FWK_WEB_SERVER_HOST_EXTERNAL_IP
export GUI_FWK_WEB_SERVER_PORT

#
#  GUI FWK API -- configuration
#

GUI_FWK_API_SERVER=
GUI_FWK_API_SERVER_HOST=
GUI_FWK_API_SERVER_HOST_IP=
GUI_FWK_API_SERVER_HOST_EXTERNAL_IP=
GUI_FWK_API_SERVER_PORT=
for TUPLE in $GUI_FWK_API_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   if [ -z "$GUI_FWK_API_SERVER" ]; then
     GUI_FWK_API_SERVER="$HOST:$PORT"
     GUI_FWK_API_SERVER_HOST="$HOST"
     GUI_FWK_API_SERVER_HOST_IP="$HOST_IP"
     GUI_FWK_API_SERVER_HOST_EXTERNAL_IP="$HOST_EXTERNAL_IP"
     GUI_FWK_API_SERVER_PORT="$PORT"
   fi
done
export GUI_FWK_API_SERVER
export GUI_FWK_API_SERVER_HOST
export GUI_FWK_API_SERVER_HOST_IP
export GUI_FWK_API_SERVER_HOST_EXTERNAL_IP
export GUI_FWK_API_SERVER_PORT

#
#  GUI FWK AUTH -- configuration
#

GUI_FWK_AUTH_SERVER=
GUI_FWK_AUTH_SERVER_HOST=
GUI_FWK_AUTH_SERVER_HOST_IP=
GUI_FWK_AUTH_SERVER_HOST_EXTERNAL_IP=
GUI_FWK_AUTH_SERVER_PORT=
for TUPLE in $GUI_FWK_AUTH_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   if [ -z "$GUI_FWK_AUTH_SERVER" ]; then
     GUI_FWK_AUTH_SERVER="$HOST:$PORT"
     GUI_FWK_AUTH_SERVER_HOST="$HOST"
     GUI_FWK_AUTH_SERVER_HOST_IP="$HOST_IP"
     GUI_FWK_AUTH_SERVER_HOST_EXTERNAL_IP="$HOST_EXTERNAL_IP"
     GUI_FWK_AUTH_SERVER_PORT="$PORT"
   fi
done
export GUI_FWK_AUTH_SERVER
export GUI_FWK_AUTH_SERVER_HOST
export GUI_FWK_AUTH_SERVER_HOST_IP
export GUI_FWK_AUTH_SERVER_HOST_EXTERNAL_IP
export GUI_FWK_AUTH_SERVER_PORT

#
#  GUI CSR WEB -- configuration
#

GUI_CSR_WEB_SERVER=
GUI_CSR_WEB_SERVER_HOST=
GUI_CSR_WEB_SERVER_HOST_IP=
GUI_CSR_WEB_SERVER_HOST_EXTERNAL_IP=
GUI_CSR_WEB_SERVER_PORT=
for TUPLE in $GUI_CSR_WEB_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNALIP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   if [ -z "$GUI_CSR_WEB_SERVER" ]; then
     GUI_CSR_WEB_SERVER="$HOST:$PORT"
     GUI_CSR_WEB_SERVER_HOST="$HOST"
     GUI_CSR_WEB_SERVER_HOST_IP="$HOST_IP"
     GUI_CSR_WEB_SERVER_HOST_EXTERNAL_IP="$HOST_EXTERNALIP"
     GUI_CSR_WEB_SERVER_PORT="$PORT"
   fi
done
export GUI_CSR_WEB_SERVER
export GUI_CSR_WEB_SERVER_HOST
export GUI_CSR_WEB_SERVER_HOST_IP
export GUI_CSR_WEB_SERVER_HOST_EXTERNAL_IP
export GUI_CSR_WEB_SERVER_PORT

#
#  GUI CSR API -- configuration
#

GUI_CSR_API_SERVER=
GUI_CSR_API_SERVER_HOST=
GUI_CSR_API_SERVER_HOST_IP=
GUI_CSR_API_SERVER_HOST_EXTERNAL_IP=
GUI_CSR_API_SERVER_PORT=
for TUPLE in $GUI_CSR_API_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   if [ -z "$GUI_CSR_API_SERVER" ]; then
     GUI_CSR_API_SERVER="$HOST:$PORT"
     GUI_CSR_API_SERVER_HOST="$HOST"
     GUI_CSR_API_SERVER_HOST_IP="$HOST_IP"
     GUI_CSR_API_SERVER_HOST_EXTERNAL_IP="$HOST_EXTERNAL_IP"
     GUI_CSR_API_SERVER_PORT="$PORT"
   fi
done
export GUI_CSR_API_SERVER
export GUI_CSR_API_SERVER_HOST
export GUI_CSR_API_SERVER_HOST_IP
export GUI_CSR_API_SERVER_HOST_EXTERNAL_IP
export GUI_CSR_API_SERVER_PORT

#
#  GUI ITM WEB -- configuration
#

GUI_ITM_WEB_SERVER=
GUI_ITM_WEB_SERVER_HOST=
GUI_ITM_WEB_SERVER_HOST_IP=
GUI_ITM_WEB_SERVER_HOST_EXTERNAL_IP=
GUI_ITM_WEB_SERVER_PORT=
for TUPLE in $GUI_ITM_WEB_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   if [ -z "$GUI_ITM_WEB_SERVER" ]; then
     GUI_ITM_WEB_SERVER="$HOST:$PORT"
     GUI_ITM_WEB_SERVER_HOST="$HOST"
     GUI_ITM_WEB_SERVER_HOST_IP="$HOST_IP"
     GUI_ITM_WEB_SERVER_HOST_EXTERNAL_IP="$HOST_EXTERNAL_IP"
     GUI_ITM_WEB_SERVER_PORT="$PORT"
   fi
done
export GUI_ITM_WEB_SERVER
export GUI_ITM_WEB_SERVER_HOST
export GUI_ITM_WEB_SERVER_HOST_IP
export GUI_ITM_WEB_SERVER_HOST_EXTERNAL_IP
export GUI_ITM_WEB_SERVER_PORT

#
#  GUI ITM API -- configuration
#

GUI_ITM_API_SERVER=
GUI_ITM_API_SERVER_HOST=
GUI_ITM_API_SERVER_HOST_IP=
GUI_ITM_API_SERVER_HOST_EXTERNAL_IP=
GUI_ITM_API_SERVER_PORT=
for TUPLE in $GUI_ITM_API_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   if [ -z "$GUI_ITM_API_SERVER" ]; then
     GUI_ITM_API_SERVER="$HOST:$PORT"
     GUI_ITM_API_SERVER_HOST="$HOST"
     GUI_ITM_API_SERVER_HOST_IP="$HOST_IP"
     GUI_ITM_API_SERVER_HOST_EXTERNAL_IP="$HOST_EXTERNAL_IP"
     GUI_ITM_API_SERVER_PORT="$PORT"
   fi
done
export GUI_ITM_API_SERVER
export GUI_ITM_API_SERVER_HOST
export GUI_ITM_API_SERVER_HOST_IP
export GUI_ITM_API_SERVER_HOST_EXTERNAL_IP
export GUI_ITM_API_SERVER_PORT

#
#  GUI JMR WEB -- configuration
#

GUI_JMR_WEB_SERVER=
GUI_JMR_WEB_SERVER_HOST=
GUI_JMR_WEB_SERVER_HOST_IP=
GUI_JMR_WEB_SERVER_HOST_EXTERNAL_IP=
GUI_JMR_WEB_SERVER_PORT=
for TUPLE in $GUI_JMR_WEB_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   if [ -z "$GUI_JMR_WEB_SERVER" ]; then
     GUI_JMR_WEB_SERVER="$HOST:$PORT"
     GUI_JMR_WEB_SERVER_HOST="$HOST"
     GUI_JMR_WEB_SERVER_HOST_IP="$HOST_IP"
     GUI_JMR_WEB_SERVER_HOST_EXTERNAL_IP="$HOST_EXTERNAL_IP"
     GUI_JMR_WEB_SERVER_PORT="$PORT"
   fi
done
export GUI_JMR_WEB_SERVER
export GUI_JMR_WEB_SERVER_HOST
export GUI_JMR_WEB_SERVER_HOST_IP
export GUI_JMR_WEB_SERVER_HOST_EXTERNAL_IP
export GUI_JMR_WEB_SERVER_PORT

#
#  GUI JMR API -- configuration
#

GUI_JMR_API_SERVER=
GUI_JMR_API_SERVER_HOST=
GUI_JMR_API_SERVER_HOST_IP=
GUI_JMR_API_SERVER_HOST_EXTERNAL_IP=
GUI_JMR_API_SERVER_PORT=
for TUPLE in $GUI_JMR_API_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   if [ -z "$GUI_JMR_API_SERVER" ]; then
     GUI_JMR_API_SERVER="$HOST:$PORT"
     GUI_JMR_API_SERVER_HOST="$HOST"
     GUI_JMR_API_SERVER_HOST_IP="$HOST_IP"
     GUI_JMR_API_SERVER_HOST_EXTERNAL_IP="$HOST_EXTERNAL_IP"
     GUI_JMR_API_SERVER_PORT="$PORT"
   fi
done
export GUI_JMR_API_SERVER
export GUI_JMR_API_SERVER_HOST
export GUI_JMR_API_SERVER_HOST_IP
export GUI_JMR_API_SERVER_HOST_EXTERNAL_IP
export GUI_JMR_API_SERVER_PORT

#
#  GUI OPC WEB -- configuration
#

GUI_OPC_WEB_SERVER=
GUI_OPC_WEB_SERVER_HOST=
GUI_OPC_WEB_SERVER_HOST_IP=
GUI_OPC_WEB_SERVER_HOST_EXTERNAL_IP=
GUI_OPC_WEB_SERVER_PORT=
for TUPLE in $GUI_OPC_WEB_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   if [ -z "$GUI_OPC_WEB_SERVER" ]; then
     GUI_OPC_WEB_SERVER="$HOST:$PORT"
     GUI_OPC_WEB_SERVER_HOST="$HOST"
     GUI_OPC_WEB_SERVER_HOST_IP="$HOST_IP"
     GUI_OPC_WEB_SERVER_HOST_EXTERNAL_IP="$HOST_EXTERNAL_IP"
     GUI_OPC_WEB_SERVER_PORT="$PORT"
   fi
done
export GUI_OPC_WEB_SERVER
export GUI_OPC_WEB_SERVER_HOST
export GUI_OPC_WEB_SERVER_HOST_IP
export GUI_OPC_WEB_SERVER_HOST_EXTERNAL_IP
export GUI_OPC_WEB_SERVER_PORT

#
#  GUI OPC API -- configuration
#

GUI_OPC_API_SERVER=
GUI_OPC_API_SERVER_HOST=
GUI_OPC_API_SERVER_HOST_IP=
GUI_OPC_API_SERVER_HOST_EXTERNAL_IP=
GUI_OPC_API_SERVER_PORT=
for TUPLE in $GUI_OPC_API_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   if [ -z "$GUI_OPC_API_SERVER" ]; then
     GUI_OPC_API_SERVER="$HOST:$PORT"
     GUI_OPC_API_SERVER_HOST="$HOST"
     GUI_OPC_API_SERVER_HOST_IP="$HOST_IP"
     GUI_OPC_API_SERVER_HOST_EXTERNAL_IP="$HOST_EXTERNAL_IP"
     GUI_OPC_API_SERVER_PORT="$PORT"
   fi
done
export GUI_OPC_API_SERVER
export GUI_OPC_API_SERVER_HOST
export GUI_OPC_API_SERVER_HOST_IP
export GUI_OPC_API_SERVER_HOST_EXTERNAL_IP
export GUI_OPC_API_SERVER_PORT

#
#  GUI IAR WEB -- configuration
#

GUI_IAR_WEB_SERVER=
GUI_IAR_WEB_SERVER_HOST=
GUI_IAR_WEB_SERVER_HOST_IP=
GUI_IAR_WEB_SERVER_HOST_EXTERNAL_IP=
GUI_IAR_WEB_SERVER_PORT=
for TUPLE in $GUI_IAR_WEB_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   if [ -z "$GUI_IAR_WEB_SERVER" ]; then
     GUI_IAR_WEB_SERVER="$HOST:$PORT"
     GUI_IAR_WEB_SERVER_HOST="$HOST"
     GUI_IAR_WEB_SERVER_HOST_IP="$HOST_IP"
     GUI_IAR_WEB_SERVER_HOST_EXTERNAL_IP="$HOST_EXTERNAL_IP"
     GUI_IAR_WEB_SERVER_PORT="$PORT"
   fi
done
export GUI_IAR_WEB_SERVER
export GUI_IAR_WEB_SERVER_HOST
export GUI_IAR_WEB_SERVER_HOST_IP
export GUI_IAR_WEB_SERVER_HOST_EXTERNAL_IP
export GUI_IAR_WEB_SERVER_PORT

#
#  GUI IAR API -- configuration
#

GUI_IAR_API_SERVER=
GUI_IAR_API_SERVER_HOST=
GUI_IAR_API_SERVER_HOST_IP=
GUI_IAR_API_SERVER_HOST_EXTERNAL_IP=
GUI_IAR_API_SERVER_PORT=
for TUPLE in $GUI_IAR_API_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   if [ -z "$GUI_IAR_API_SERVER" ]; then
     GUI_IAR_API_SERVER="$HOST:$PORT"
     GUI_IAR_API_SERVER_HOST="$HOST"
     GUI_IAR_API_SERVER_HOST_IP="$HOST_IP"
     GUI_IAR_API_SERVER_HOST_EXTERNAL_IP="$HOST_EXTERNAL_IP"
     GUI_IAR_API_SERVER_PORT="$PORT"
   fi
done
export GUI_IAR_API_SERVER
export GUI_IAR_API_SERVER_HOST
export GUI_IAR_API_SERVER_HOST_IP
export GUI_IAR_API_SERVER_HOST_EXTERNAL_IP
export GUI_IAR_API_SERVER_PORT

#
#  GUI OPR WEB -- configuration
#

GUI_OPR_WEB_SERVER=
GUI_OPR_WEB_SERVER_HOST=
GUI_OPR_WEB_SERVER_HOST_IP=
GUI_OPR_WEB_SERVER_HOST_EXTERNAL_IP=
GUI_OPR_WEB_SERVER_PORT=
for TUPLE in $GUI_OPR_WEB_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   if [ -z "$GUI_OPR_WEB_SERVER" ]; then
     GUI_OPR_WEB_SERVER="$HOST:$PORT"
     GUI_OPR_WEB_SERVER_HOST="$HOST"
     GUI_OPR_WEB_SERVER_HOST_IP="$HOST_IP"
     GUI_OPR_WEB_SERVER_HOST_EXTERNAL_IP="$HOST_EXTERNAL_IP"
     GUI_OPR_WEB_SERVER_PORT="$PORT"
   fi
done
export GUI_OPR_WEB_SERVER
export GUI_OPR_WEB_SERVER_HOST
export GUI_OPR_WEB_SERVER_HOST_IP
export GUI_OPR_WEB_SERVER_HOST_EXTERNAL_IP
export GUI_OPR_WEB_SERVER_PORT

#
#  GUI OPR API -- configuration
#

GUI_OPR_API_SERVER=
GUI_OPR_API_SERVER_HOST=
GUI_OPR_API_SERVER_HOST_IP=
GUI_OPR_API_SERVER_HOST_EXTERNAL_IP=
GUI_OPR_API_SERVER_PORT=
for TUPLE in $GUI_OPR_API_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   if [ -z "$GUI_OPR_API_SERVER" ]; then
     GUI_OPR_API_SERVER="$HOST:$PORT"
     GUI_OPR_API_SERVER_HOST="$HOST"
     GUI_OPR_API_SERVER_HOST_IP="$HOST_IP"
     GUI_OPR_API_SERVER_HOST_EXTERNAL_IP="$HOST_EXTERNAL_IP"
     GUI_OPR_API_SERVER_PORT="$PORT"
   fi
done
export GUI_OPR_API_SERVER
export GUI_OPR_API_SERVER_HOST
export GUI_OPR_API_SERVER_HOST_IP
export GUI_OPR_API_SERVER_HOST_EXTERNAL_IP
export GUI_OPR_API_SERVER_PORT

#
#  GUI STG WEB -- configuration
#

GUI_STG_WEB_SERVER=
GUI_STG_WEB_SERVER_HOST=
GUI_STG_WEB_SERVER_HOST_IP=
GUI_STG_WEB_SERVER_HOST_EXTERNAL_IP=
GUI_STG_WEB_SERVER_PORT=
for TUPLE in $GUI_STG_WEB_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   if [ -z "$GUI_STG_WEB_SERVER" ]; then
     GUI_STG_WEB_SERVER="$HOST:$PORT"
     GUI_STG_WEB_SERVER_HOST="$HOST"
     GUI_STG_WEB_SERVER_HOST_IP="$HOST_IP"
     GUI_STG_WEB_SERVER_HOST_EXTERNAL_IP="$HOST_EXTERNAL_IP"
     GUI_STG_WEB_SERVER_PORT="$PORT"
   fi
done
export GUI_STG_WEB_SERVER
export GUI_STG_WEB_SERVER_HOST
export GUI_STG_WEB_SERVER_HOST_IP
export GUI_STG_WEB_SERVER_HOST_EXTERNAL_IP
export GUI_STG_WEB_SERVER_PORT

#
#  GUI STG API -- configuration
#

GUI_STG_API_SERVER=
GUI_STG_API_SERVER_HOST=
GUI_STG_API_SERVER_HOST_IP=
GUI_STG_API_SERVER_HOST_EXTERNAL_IP=
GUI_STG_API_SERVER_PORT=
for TUPLE in $GUI_STG_API_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   if [ -z "$GUI_STG_API_SERVER" ]; then
     GUI_STG_API_SERVER="$HOST:$PORT"
     GUI_STG_API_SERVER_HOST="$HOST"
     GUI_STG_API_SERVER_HOST_IP="$HOST_IP"
     GUI_STG_API_SERVER_HOST_EXTERNAL_IP="$HOST_EXTERNAL_IP"
     GUI_STG_API_SERVER_PORT="$PORT"
   fi
done
export GUI_STG_API_SERVER
export GUI_STG_API_SERVER_HOST
export GUI_STG_API_SERVER_HOST_IP
export GUI_STG_API_SERVER_HOST_EXTERNAL_IP
export GUI_STG_API_SERVER_PORT

#
#  GUI SBM WEB -- configuration
#

GUI_SBM_WEB_SERVER=
GUI_SBM_WEB_SERVER_HOST=
GUI_SBM_WEB_SERVER_HOST_IP=
GUI_SBM_WEB_SERVER_HOST_EXTERNAL_IP=
GUI_SBM_WEB_SERVER_PORT=
for TUPLE in $GUI_SBM_WEB_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   if [ -z "$GUI_SBM_WEB_SERVER" ]; then
     GUI_SBM_WEB_SERVER="$HOST:$PORT"
     GUI_SBM_WEB_SERVER_HOST="$HOST"
     GUI_SBM_WEB_SERVER_HOST_IP="$HOST_IP"
     GUI_SBM_WEB_SERVER_HOST_EXTERNAL_IP="$HOST_EXTERNAL_IP"
     GUI_SBM_WEB_SERVER_PORT="$PORT"
   fi
done
export GUI_SBM_WEB_SERVER
export GUI_SBM_WEB_SERVER_HOST
export GUI_SBM_WEB_SERVER_HOST_IP
export GUI_SBM_WEB_SERVER_HOST_EXTERNAL_IP
export GUI_SBM_WEB_SERVER_PORT

#
#  GUI SBM API -- configuration
#

GUI_SBM_API_SERVER=
GUI_SBM_API_SERVER_HOST=
GUI_SBM_API_SERVER_HOST_IP=
GUI_SBM_API_SERVER_HOST_EXTERNAL_IP=
GUI_SBM_API_SERVER_PORT=
for TUPLE in $GUI_SBM_API_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   if [ -z "$GUI_SBM_API_SERVER" ]; then
     GUI_SBM_API_SERVER="$HOST:$PORT"
     GUI_SBM_API_SERVER_HOST="$HOST"
     GUI_SBM_API_SERVER_HOST_IP="$HOST_IP"
     GUI_SBM_API_SERVER_HOST_EXTERNAL_IP="$HOST_EXTERNAL_IP"
     GUI_SBM_API_SERVER_PORT="$PORT"
   fi
done
export GUI_SBM_API_SERVER
export GUI_SBM_API_SERVER_HOST
export GUI_SBM_API_SERVER_HOST_IP
export GUI_SBM_API_SERVER_HOST_EXTERNAL_IP
export GUI_SBM_API_SERVER_PORT

#
#  GUI LPM WEB -- configuration
#

GUI_LPM_WEB_SERVER=
GUI_LPM_WEB_SERVER_HOST=
GUI_LPM_WEB_SERVER_HOST_IP=
GUI_LPM_WEB_SERVER_HOST_EXTERNAL_IP=
GUI_LPM_WEB_SERVER_PORT=
for TUPLE in $GUI_LPM_WEB_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   if [ -z "$GUI_LPM_WEB_SERVER" ]; then
     GUI_LPM_WEB_SERVER="$HOST:$PORT"
     GUI_LPM_WEB_SERVER_HOST="$HOST"
     GUI_LPM_WEB_SERVER_HOST_IP="$HOST_IP"
     GUI_LPM_WEB_SERVER_HOST_EXTERNAL_IP="$HOST_EXTERNAL_IP"
     GUI_LPM_WEB_SERVER_PORT="$PORT"
   fi
done
export GUI_LPM_WEB_SERVER
export GUI_LPM_WEB_SERVER_HOST
export GUI_LPM_WEB_SERVER_HOST_IP
export GUI_LPM_WEB_SERVER_HOST_EXTERNAL_IP
export GUI_LPM_WEB_SERVER_PORT

#
#  GUI LPM API -- configuration
#

GUI_LPM_API_SERVER=
GUI_LPM_API_SERVER_HOST=
GUI_LPM_API_SERVER_HOST_IP=
GUI_LPM_API_SERVER_HOST_EXTERNAL_IP=
GUI_LPM_API_SERVER_PORT=
for TUPLE in $GUI_LPM_API_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   if [ -z "$GUI_LPM_API_SERVER" ]; then
     GUI_LPM_API_SERVER="$HOST:$PORT"
     GUI_LPM_API_SERVER_HOST="$HOST"
     GUI_LPM_API_SERVER_HOST_IP="$HOST_IP"
     GUI_LPM_API_SERVER_HOST_EXTERNAL_IP="$HOST_EXTERNAL_IP"
     GUI_LPM_API_SERVER_PORT="$PORT"
   fi
done
export GUI_LPM_API_SERVER
export GUI_LPM_API_SERVER_HOST
export GUI_LPM_API_SERVER_HOST_IP
export GUI_LPM_API_SERVER_HOST_EXTERNAL_IP
export GUI_LPM_API_SERVER_PORT

#
#  GUI AUDIT -- configuration
#

GUI_AUDIT_SERVER_HOST=
for TUPLE in $GUI_AUDIT_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   if [ -z "$GUI_AUDIT_SERVER_HOST" ]; then
     GUI_AUDIT_SERVER_HOST="$HOST"
   fi
done
export GUI_AUDIT_SERVER_HOST
#
#  thirdpartymanager -- configuration
#

THIRDPARTYMANAGER_CONFIGURATION=`echo $THIRDPARTYMANAGER_CONFIGURATION | sed 's/ /\n/g' | uniq`
THIRDPARTYMANAGER_PROMETHEUS=
THIRDPARTYMANAGER_ENABLED=false
for TUPLE in $THIRDPARTYMANAGER_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export API_PORT=`echo $TUPLE | cut -d: -f3`
   export MONITORING_PORT=`echo $TUPLE | cut -d: -f4`
   export THREADPOOL_SIZE=`echo $TUPLE | cut -d: -f5`
   export DEBUG_PORT=`echo $TUPLE | cut -d: -f6`
   if [ -n "$THIRDPARTYMANAGER_PROMETHEUS" ]; then
     THIRDPARTYMANAGER_PROMETHEUS="$THIRDPARTYMANAGER_PROMETHEUS,'$HOST:$MONITORING_PORT'"
   else
     THIRDPARTYMANAGER_PROMETHEUS="'$HOST:$MONITORING_PORT'"
   fi
   THIRDPARTYMANAGER_ENABLED=true
done
export THIRDPARTYMANAGER_PROMETHEUS
export THIRDPARTYMANAGER_ENABLED

#
#  dnboproxy -- configuration
#

DNBOPROXY_CONFIGURATION=`echo $DNBOPROXY_CONFIGURATION | sed 's/ /\n/g' | uniq`
DNBOPROXY_SERVER=
DNBOPROXY_PROMETHEUS=
DNBOPROXY_ENABLED=false
for TUPLE in $DNBOPROXY_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export API_PORT=`echo $TUPLE | cut -d: -f3`
   export MONITORING_PORT=`echo $TUPLE | cut -d: -f4`
   if [ -z "$DNBOPROXY_SERVER" ]; then
     DNBOPROXY_SERVER="$HOST:$API_PORT"
   fi
   if [ -n "$DNBOPROXY_PROMETHEUS" ]; then
     DNBOPROXY_PROMETHEUS="$DNBOPROXY_PROMETHEUS,'$HOST:$MONITORING_PORT'"
   else
     DNBOPROXY_PROMETHEUS="'$HOST:$MONITORING_PORT'"
   fi
   DNBOPROXY_ENABLED=true
done
export DNBOPROXY_SERVER
export DNBOPROXY_PROMETHEUS
export DNBOPROXY_ENABLED

#
#  Fake SMSC -- configuration
#

FAKE_SMSC_CONFIGURATION=`echo $FAKE_SMSC_CONFIGURATION | sed 's/ /\n/g' | uniq`
SMSC_CONNECTION_1=
for TUPLE in $FAKE_SMSC_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export SMPP_PORT=`echo $TUPLE | cut -d: -f4`
   export HTTP_PORT=`echo $TUPLE | cut -d: -f5`
   if [ -z "$SMSC_CONNECTION_1" ]; then
     SMSC_CONNECTION_1="$HOST_IP:$SMPP_PORT"
     SMSC_HOST="$HOST_IP"
     SMSC_PORT="$SMPP_PORT"
   fi
done
export SMSC_CONNECTION_1
export SMSC_HOST
export SMSC_PORT

#
#  Fake SMTP -- configuration
#

FAKE_SMTP_CONFIGURATION=`echo $FAKE_SMTP_CONFIGURATION | sed 's/ /\n/g' | uniq`
SMTP_HOST_1=
SMTP_PORT_1=
for TUPLE in $FAKE_SMTP_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export SMTP_PORT=`echo $TUPLE | cut -d: -f3`
   if [ -z "$SMTP_HOST_1" ]; then
     SMTP_HOST_1="$HOST"
     SMTP_PORT_1="$SMTP_PORT"
   fi
done
export SMTP_HOST_1
export SMTP_PORT_1

#
#  Fake IN -- configuration
#

for TUPLE in $FAKE_IN_IN_SERVERS
do
  export KEY=`echo $TUPLE | cut -d: -f1`
  export HOST=`echo $TUPLE | cut -d: -f2`
  export PORT=`echo $TUPLE | cut -d: -f3`
  export DETERMINISTIC=`echo $TUPLE | cut -d: -f4`
  declare FAKE_IN_IN_SERVERS_$KEY="$HOST:$PORT"
  DYNAMIC_IN=FAKE_IN_IN_SERVERS_$KEY
  export ${DYNAMIC_IN}
done

#
# Kafka Lag Exporter 
#

KAFKA_LAG_EXPORTER=`echo $KAFKA_LAG_EXPORTER | sed 's/ /\n/g' | uniq`
KAFKA_LAG_EXPORTER_URL=
KAFKA_LAG_EXPORTER_PORT=
for TUPLE in $KAFKA_LAG_EXPORTER
do  
  export HOST=`echo $TUPLE | cut -d: -f2`
  export PORT=`echo $TUPLE | cut -d: -f3`    
  KAFKA_LAG_EXPORTER_URL="'$HOST:$PORT'"
  KAFKA_LAG_EXPORTER_PORT="$PORT"
done
export KAFKA_LAG_EXPORTER_URL
export KAFKA_LAG_EXPORTER_PORT

#
#  CSR mockup
#

if [ "${GUI_USE_CSR_MOCKUP}" = "true" ]; then
  EFFECTIVE_CSR_GUIMANAGER_PORT=7082
else
  EFFECTIVE_CSR_GUIMANAGER_PORT=${GUIMANAGER_PORT}
fi
export EFFECTIVE_CSR_GUIMANAGER_PORT

#########################################
#
#  heap opts
#
#########################################

export GUIMANAGER_HEAP_OPTS="-Xms$GUIMANAGER_MEMORY -Xmx$GUIMANAGER_MEMORY"
export THIRDPARTYMANAGER_HEAP_OPTS="-Xms$THIRDPARTYMANAGER_MEMORY -Xmx$THIRDPARTYMANAGER_MEMORY"
export DNBOPROXY_HEAP_OPTS="-Xms$DNBOPROXY_MEMORY -Xmx$DNBOPROXY_MEMORY"
export EVOLUTIONENGINE_HEAP_OPTS="-Xms$EVOLUTIONENGINE_MEMORY -Xmx$EVOLUTIONENGINE_MEMORY"
export JOURNEYTRAFFICENGINE_HEAP_OPTS="-Xms$JOURNEYTRAFFICENGINE_MEMORY -Xmx$JOURNEYTRAFFICENGINE_MEMORY"
export UCGENGINE_HEAP_OPTS="-Xms$UCGENGINE_MEMORY -Xmx$UCGENGINE_MEMORY"
export INFULFILLMENTMANAGER_HEAP_OPTS="-Xms$INFULFILLMENTMANAGER_MEMORY -Xmx$INFULFILLMENTMANAGER_MEMORY"
export EMPTYFULFILLMENTMANAGER_HEAP_OPTS="-Xms$EMPTYFULFILLMENTMANAGER_MEMORY -Xmx$EMPTYFULFILLMENTMANAGER_MEMORY"
export COMMODITYDELIVERYMANAGER_HEAP_OPTS="-Xms$COMMODITYDELIVERYMANAGER_MEMORY -Xmx$COMMODITYDELIVERYMANAGER_MEMORY"
export PURCHASEFULFILLMENTMANAGER_HEAP_OPTS="-Xms$PURCHASEFULFILLMENTMANAGER_MEMORY -Xmx$PURCHASEFULFILLMENTMANAGER_MEMORY"
export NOTIFICATIONMANAGER_SMS_HEAP_OPTS="-Xms$NOTIFICATIONMANAGER_SMS_MEMORY -Xmx$NOTIFICATIONMANAGER_SMS_MEMORY"
export NOTIFICATIONMANAGER_MAIL_HEAP_OPTS="-Xms$NOTIFICATIONMANAGER_MAIL_MEMORY -Xmx$NOTIFICATIONMANAGER_MAIL_MEMORY"
export NOTIFICATIONMANAGER_PUSH_HEAP_OPTS="-Xms$NOTIFICATIONMANAGER_PUSH_MEMORY -Xmx$NOTIFICATIONMANAGER_PUSH_MEMORY"
export REPORTMANAGER_HEAP_OPTS="-Xms$REPORTMANAGER_MEMORY -Xmx$REPORTMANAGER_MEMORY"
export REPORTSCHEDULER_HEAP_OPTS="-Xms$REPORTSCHEDULER_MEMORY -Xmx$REPORTSCHEDULER_MEMORY"
export SUBSCRIBERGROUP_HEAP_OPTS="-Xms$SUBSCRIBERGROUP_MEMORY -Xmx$SUBSCRIBERGROUP_MEMORY"

#########################################
#
#  heap opts
#
#########################################

export GUIMANAGER_CONTAINER_MEMORY_LIMIT=$(memory_limit $GUIMANAGER_MEMORY)
export THIRDPARTYMANAGER_CONTAINER_MEMORY_LIMIT=$(memory_limit $THIRDPARTYMANAGER_MEMORY)
export DNBOPROXY_CONTAINER_MEMORY_LIMIT=$(memory_limit $DNBOPROXY_MEMORY)
export EVOLUTIONENGINE_CONTAINER_MEMORY_LIMIT=$(memory_limit $EVOLUTIONENGINE_MEMORY)
export JOURNEYTRAFFICENGINE_CONTAINER_MEMORY_LIMIT=$(memory_limit $JOURNEYTRAFFICENGINE_MEMORY)
export UCGENGINE_CONTAINER_MEMORY_LIMIT=$(memory_limit $UCGENGINE_MEMORY)
export INFULFILLMENTMANAGER_CONTAINER_MEMORY_LIMIT=$(memory_limit $INFULFILLMENTMANAGER_MEMORY)
export EMPTYFULFILLMENTMANAGER_CONTAINER_MEMORY_LIMIT=$(memory_limit $EMPTYFULFILLMENTMANAGER_MEMORY)
export COMMODITYDELIVERYMANAGER_CONTAINER_MEMORY_LIMIT=$(memory_limit $COMMODITYDELIVERYMANAGER_MEMORY)
export PURCHASEFULFILLMENTMANAGER_HEAP_OPTS="-Xms$PURCHASEFULFILLMENTMANAGER_MEMORY -Xmx$PURCHASEFULFILLMENTMANAGER_MEMORY"
export NOTIFICATIONMANAGER_SMS_CONTAINER_MEMORY_LIMIT=$(memory_limit $NOTIFICATIONMANAGER_SMS_MEMORY)
export NOTIFICATIONMANAGER_MAIL_CONTAINER_MEMORY_LIMIT=$(memory_limit $NOTIFICATIONMANAGER_MAIL_MEMORY)
export NOTIFICATIONMANAGER_PUSH_CONTAINER_MEMORY_LIMIT=$(memory_limit $NOTIFICATIONMANAGER_PUSH_MEMORY)
export REPORTMANAGER_CONTAINER_MEMORY_LIMIT=$(memory_limit $REPORTMANAGER_MEMORY)
export REPORTSCHEDULER_CONTAINER_MEMORY_LIMIT=$(memory_limit $REPORTSCHEDULER_MEMORY)
export SUBSCRIBERGROUP_CONTAINER_MEMORY_LIMIT=$(memory_limit $SUBSCRIBERGROUP_MEMORY)

