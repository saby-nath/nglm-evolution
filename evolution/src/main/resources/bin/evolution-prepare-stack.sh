#################################################################################
#
#  evolution-prepare-stack.sh
#
#################################################################################

#########################################
#
#  construct resources
#
#########################################

#
#  update-subscribergroup.sh
#

cat $DEPLOY_ROOT/bin/resources/update-subscribergroup.sh | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' > $DEPLOY_ROOT/bin/update-subscribergroup.sh
chmod 755 $DEPLOY_ROOT/bin/update-subscribergroup.sh

#
#  storeconfiguration.sh
#

cat $DEPLOY_ROOT/bin/resources/storeconfiguration.sh | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' > $DEPLOY_ROOT/bin/storeconfiguration.sh
chmod 755 $DEPLOY_ROOT/bin/storeconfiguration.sh

#
#  configlog.sh
#

cat $DEPLOY_ROOT/bin/resources/configlog.sh | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' > $DEPLOY_ROOT/bin/configlog.sh
chmod 755 $DEPLOY_ROOT/bin/configlog.sh

#
#  cleanup.sh
#

cat $DEPLOY_ROOT/bin/resources/cleanup.sh | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' > $DEPLOY_ROOT/bin/cleanup.sh
chmod 755 $DEPLOY_ROOT/bin/cleanup.sh

#
#  restorekafkabackup.sh
#

cat $DEPLOY_ROOT/bin/resources/restoreKafkaBackup.sh | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' > $DEPLOY_ROOT/bin/restoreKafkaBackup.sh
chmod 755 $DEPLOY_ROOT/bin/restoreKafkaBackup.sh

#########################################
#
#  construct stack -- application monitoring
#
#########################################

#
#  preamble
#

mkdir -p $DEPLOY_ROOT/stack
cat $DEPLOY_ROOT/docker/stack-preamble.yml > $DEPLOY_ROOT/stack/stack-application-monitoring.yml

#
#  prometheus-application -- services
#

cat $DEPLOY_ROOT/docker/prometheus-application.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-application-monitoring.yml

#
# configs
#

cat $DEPLOY_ROOT/docker/prometheus-application-stack-configs.yml >> $DEPLOY_ROOT/stack/stack-application-monitoring.yml

#
#  postamble
#

echo >> $DEPLOY_ROOT/stack/stack-application-monitoring.yml
cat $DEPLOY_ROOT/docker/stack-postamble.yml >> $DEPLOY_ROOT/stack/stack-application-monitoring.yml

#########################################
#
#  construct stack -- guimanager
#
#########################################

#
#  preamble
#

mkdir -p $DEPLOY_ROOT/stack
cat $DEPLOY_ROOT/docker/stack-preamble.yml > $DEPLOY_ROOT/stack/stack-guimanager.yml

#
#  guimanager
#

cat $DEPLOY_ROOT/docker/guimanager.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-guimanager.yml
echo >> $DEPLOY_ROOT/stack/stack-guimanager.yml

#
#  postamble
#

cat $DEPLOY_ROOT/docker/stack-postamble.yml >> $DEPLOY_ROOT/stack/stack-guimanager.yml

#########################################
#
#  construct stack -- thirdpartymanager(if necessary)
#
#########################################

if [ "$THIRDPARTYMANAGER_ENABLED" = "true" ]; then

  #
  #  preamble
  #

  mkdir -p $DEPLOY_ROOT/stack
  cat $DEPLOY_ROOT/docker/stack-preamble.yml > $DEPLOY_ROOT/stack/stack-thirdpartymanager.yml

  #
  #  thirdpartymanager
  #

  for TUPLE in $THIRDPARTYMANAGER_CONFIGURATION
  do
     export KEY=`echo $TUPLE | cut -d: -f1`
     export HOST=`echo $TUPLE | cut -d: -f2`
     export API_PORT=`echo $TUPLE | cut -d: -f3`
     export MONITORING_PORT=`echo $TUPLE | cut -d: -f4`
     export THREADPOOL_SIZE=`echo $TUPLE | cut -d: -f5`
     export DEBUG_PORT=`echo $TUPLE | cut -d: -f6`
     cat $DEPLOY_ROOT/docker/thirdpartymanager.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-thirdpartymanager.yml
     echo >> $DEPLOY_ROOT/stack/stack-thirdpartymanager.yml

  done

  #
  # thirdparty stack config
  #
  
  cat $DEPLOY_ROOT/docker/thirdpartymanager-config.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' > $DEPLOY_ROOT/docker/thirdpartymanager-source-config.yml
  cat $DEPLOY_ROOT/docker/thirdpartymanager-source-config.yml >> $DEPLOY_ROOT/stack/stack-thirdpartymanager.yml
  echo >> $DEPLOY_ROOT/stack/stack-thirdpartymanager.yml

  #
  #  postamble
  #

  cat $DEPLOY_ROOT/docker/stack-postamble.yml >> $DEPLOY_ROOT/stack/stack-thirdpartymanager.yml
  
fi

#########################################
#
#  construct stack -- dnboproxy(if necessary)
#
#########################################

if [ "$DNBOPROXY_ENABLED" = "true" ]; then

  #
  #  preamble
  #

  mkdir -p $DEPLOY_ROOT/stack
  cat $DEPLOY_ROOT/docker/stack-preamble.yml > $DEPLOY_ROOT/stack/stack-dnboproxy.yml

  #
  #  dnboproxy
  #

  for TUPLE in $DNBOPROXY_CONFIGURATION
  do
     export KEY=`echo $TUPLE | cut -d: -f1`
     export HOST=`echo $TUPLE | cut -d: -f2`
     export API_PORT=`echo $TUPLE | cut -d: -f3`
     export MONITORING_PORT=`echo $TUPLE | cut -d: -f4`
     export DEBUG_PORT=`echo $TUPLE | cut -d: -f5`
     cat $DEPLOY_ROOT/docker/dnboproxy.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-dnboproxy.yml
     echo >> $DEPLOY_ROOT/stack/stack-dnboproxy.yml
  done

  #
  #  postamble
  #

  cat $DEPLOY_ROOT/docker/stack-postamble.yml >> $DEPLOY_ROOT/stack/stack-dnboproxy.yml
  
fi

#########################################
#
#  construct stack -- evolutionengine
#
#########################################

#
#  preamble
#

mkdir -p $DEPLOY_ROOT/stack
cat $DEPLOY_ROOT/docker/stack-preamble.yml > $DEPLOY_ROOT/stack/stack-evolutionengine.yml

#
#  evolutionengine
#

export EVOLUTION_ENGINE_REBALANCING_TIMEOUT_MS=${EVOLUTION_ENGINE_REBALANCING_TIMEOUT_MS:-600000}

for TUPLE in $EVOLUTIONENGINE_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export SUBSCRIBERPROFILE_PORT=`echo $TUPLE | cut -d: -f3`
   export INTERNAL_PORT=`echo $TUPLE | cut -d: -f4`
   export MONITORING_PORT=`echo $TUPLE | cut -d: -f5`
   export DEBUG_PORT=`echo $TUPLE | cut -d: -f6`
   cat $DEPLOY_ROOT/docker/evolutionengine.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-evolutionengine.yml
   echo >> $DEPLOY_ROOT/stack/stack-evolutionengine.yml
   
done

#
#  postamble
#

cat $DEPLOY_ROOT/docker/stack-postamble.yml >> $DEPLOY_ROOT/stack/stack-evolutionengine.yml

#########################################
#
#  construct stack -- ucgengine
#
#########################################

if [ "$UCGENGINE_ENABLED" = "true" ]; then

  #
  #  preamble
  #

  mkdir -p $DEPLOY_ROOT/stack
  cat $DEPLOY_ROOT/docker/stack-preamble.yml > $DEPLOY_ROOT/stack/stack-ucgengine.yml

  #
  #  ucgengine
  #

  for TUPLE in $UCGENGINE_CONFIGURATION
  do
     export KEY=`echo $TUPLE | cut -d: -f1`
     export HOST=`echo $TUPLE | cut -d: -f2`
     export MONITORING_PORT=`echo $TUPLE | cut -d: -f3`
     export DEBUG_PORT=`echo $TUPLE | cut -d: -f4`
     cat $DEPLOY_ROOT/docker/ucgengine.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-ucgengine.yml
     echo >> $DEPLOY_ROOT/stack/stack-ucgengine.yml
  done

  #
  #  postamble
  #

  cat $DEPLOY_ROOT/docker/stack-postamble.yml >> $DEPLOY_ROOT/stack/stack-ucgengine.yml

fi  

#########################################
#
#  construct stack -- infulfillmentmanager
#
#########################################

if [ "$INFULFILLMENTMANAGER_ENABLED" = "true" ]; then

  #
  #  preamble
  #

  mkdir -p $DEPLOY_ROOT/stack
  cat $DEPLOY_ROOT/docker/stack-preamble.yml > $DEPLOY_ROOT/stack/stack-infulfillmentmanager.yml

  #
  #  infulfillmentmanager
  #

  for TUPLE in $INFULFILLMENTMANAGER_CONFIGURATION
  do
     export KEY=`echo $TUPLE | cut -d: -f1`
     export HOST=`echo $TUPLE | cut -d: -f2`
     export MONITORING_PORT=`echo $TUPLE | cut -d: -f3`
     export DEBUG_PORT=`echo $TUPLE | cut -d: -f4`
     export PLUGIN_NAME=`echo $TUPLE | cut -d: -f5`
     export PLUGIN_CONFIGURATION=`echo $TUPLE | cut -d: -f6-`
     cat $DEPLOY_ROOT/docker/infulfillmentmanager.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-infulfillmentmanager.yml
     echo >> $DEPLOY_ROOT/stack/stack-infulfillmentmanager.yml

  done

  #
  #  postamble
  #

  cat $DEPLOY_ROOT/docker/stack-postamble.yml >> $DEPLOY_ROOT/stack/stack-infulfillmentmanager.yml

fi

#########################################
#
#  construct stack -- commoditydeliverymanager
#
#########################################

if [ "$COMMODITYDELIVERYMANAGER_ENABLED" = "true" ]; then

  #
  #  preamble
  #

  mkdir -p $DEPLOY_ROOT/stack
  cat $DEPLOY_ROOT/docker/stack-preamble.yml > $DEPLOY_ROOT/stack/stack-commoditydeliverymanager.yml

  #
  #  commoditydeliverymanager
  #

  for TUPLE in $COMMODITYDELIVERYMANAGER_CONFIGURATION
  do
     export KEY=`echo $TUPLE | cut -d: -f1`
     export HOST=`echo $TUPLE | cut -d: -f2`
     export MONITORING_PORT=`echo $TUPLE | cut -d: -f3`
     export DEBUG_PORT=`echo $TUPLE | cut -d: -f4`
     export PLUGIN_NAME=`echo $TUPLE | cut -d: -f5`
     export PLUGIN_CONFIGURATION=`echo $TUPLE | cut -d: -f6-`
     cat $DEPLOY_ROOT/docker/commoditydeliverymanager.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-commoditydeliverymanager.yml
     echo >> $DEPLOY_ROOT/stack/stack-commoditydeliverymanager.yml

  done

  #
  #  postamble
  #

  cat $DEPLOY_ROOT/docker/stack-postamble.yml >> $DEPLOY_ROOT/stack/stack-commoditydeliverymanager.yml

fi  

#########################################
#
#  construct stack -- purchasefulfillmentmanager
#
#########################################

if [ "$PURCHASEFULFILLMENTMANAGER_ENABLED" = "true" ]; then

  #
  #  preamble
  #

  mkdir -p $DEPLOY_ROOT/stack
  cat $DEPLOY_ROOT/docker/stack-preamble.yml > $DEPLOY_ROOT/stack/stack-purchasefulfillmentmanager.yml

  #
  #  purchasefulfillmentmanager
  #

  for TUPLE in $PURCHASEFULFILLMENTMANAGER_CONFIGURATION
  do
     export KEY=`echo $TUPLE | cut -d: -f1`
     export HOST=`echo $TUPLE | cut -d: -f2`
     export MONITORING_PORT=`echo $TUPLE | cut -d: -f3`
     export DEBUG_PORT=`echo $TUPLE | cut -d: -f4`
     export PLUGIN_NAME=`echo $TUPLE | cut -d: -f5`
     export PLUGIN_CONFIGURATION=`echo $TUPLE | cut -d: -f6-`
     cat $DEPLOY_ROOT/docker/purchasefulfillmentmanager.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-purchasefulfillmentmanager.yml
     echo >> $DEPLOY_ROOT/stack/stack-purchasefulfillmentmanager.yml

  done

  #
  #  postamble
  #

  cat $DEPLOY_ROOT/docker/stack-postamble.yml >> $DEPLOY_ROOT/stack/stack-purchasefulfillmentmanager.yml

fi  

#########################################
#
#  construct stack -- notificationmanager
#
#########################################

if [ "$NOTIFICATIONMANAGER_SMS_ENABLED" = "true" ]; then

  #
  #  preamble
  #

  mkdir -p $DEPLOY_ROOT/stack
  cat $DEPLOY_ROOT/docker/stack-preamble.yml > $DEPLOY_ROOT/stack/stack-notificationmanagersms.yml

  #
  #  notificationmanagersms
  #

  for TUPLE in $NOTIFICATIONMANAGER_SMS_CONFIGURATION
  do
     export KEY=`echo $TUPLE | cut -d: -f1`
     export HOST=`echo $TUPLE | cut -d: -f2`
     export MONITORING_PORT=`echo $TUPLE | cut -d: -f3`
     export DEBUG_PORT=`echo $TUPLE | cut -d: -f4`
     export PLUGIN_NAME=`echo $TUPLE | cut -d: -f5`
     export PLUGIN_CONFIGURATION=`echo $TUPLE | cut -d: -f6-`
     cat $DEPLOY_ROOT/docker/notificationmanagersms.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-notificationmanagersms.yml
     echo >> $DEPLOY_ROOT/stack/stack-notificationmanagersms.yml
     
  done

  #
  #  postamble
  #

  cat $DEPLOY_ROOT/docker/stack-postamble.yml >> $DEPLOY_ROOT/stack/stack-notificationmanagersms.yml
  
fi

#
#  notificationmanagermail
#

if [ "$NOTIFICATIONMANAGER_MAIL_ENABLED" = "true" ]; then

  #
  #  preamble
  #

  mkdir -p $DEPLOY_ROOT/stack
  cat $DEPLOY_ROOT/docker/stack-preamble.yml > $DEPLOY_ROOT/stack/stack-notificationmanagermail.yml

  #
  #  notificationmanagermail
  #

  for TUPLE in $NOTIFICATIONMANAGER_MAIL_CONFIGURATION
  do
     export KEY=`echo $TUPLE | cut -d: -f1`
     export HOST=`echo $TUPLE | cut -d: -f2`
     export MONITORING_PORT=`echo $TUPLE | cut -d: -f3`
     export DEBUG_PORT=`echo $TUPLE | cut -d: -f4`
     export PLUGIN_NAME=`echo $TUPLE | cut -d: -f5`
     export PLUGIN_CONFIGURATION=`echo $TUPLE | cut -d: -f6-`
     cat $DEPLOY_ROOT/docker/notificationmanagermail.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-notificationmanagermail.yml
     echo >> $DEPLOY_ROOT/stack/stack-notificationmanagermail.yml

  done

  #
  #  postamble
  #

  cat $DEPLOY_ROOT/docker/stack-postamble.yml >> $DEPLOY_ROOT/stack/stack-notificationmanagermail.yml

fi  

#
#  notificationmanagerpush
#

if [ "$NOTIFICATIONMANAGER_PUSH_ENABLED" = "true" ]; then

  #
  #  preamble
  #

  mkdir -p $DEPLOY_ROOT/stack
  cat $DEPLOY_ROOT/docker/stack-preamble.yml > $DEPLOY_ROOT/stack/stack-notificationmanagerpush.yml

  #
  #  notificationmanagerpush
  #

  for TUPLE in $NOTIFICATIONMANAGER_PUSH_CONFIGURATION
  do
     export KEY=`echo $TUPLE | cut -d: -f1`
     export HOST=`echo $TUPLE | cut -d: -f2`
     export MONITORING_PORT=`echo $TUPLE | cut -d: -f3`
     export DEBUG_PORT=`echo $TUPLE | cut -d: -f4`
     export PLUGIN_NAME=`echo $TUPLE | cut -d: -f5`
     export PLUGIN_CONFIGURATION=`echo $TUPLE | cut -d: -f6-`
     cat $DEPLOY_ROOT/docker/notificationmanagerpush.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-notificationmanagerpush.yml
     echo >> $DEPLOY_ROOT/stack/stack-notificationmanagerpush.yml

  done

  #
  #  postamble
  #

  cat $DEPLOY_ROOT/docker/stack-postamble.yml >> $DEPLOY_ROOT/stack/stack-notificationmanagerpush.yml

fi  


#
#  notificationmanager
#

if [ "$NOTIFICATIONMANAGER_ENABLED" = "true" ]; then 

  #
  #  preamble
  #

  mkdir -p $DEPLOY_ROOT/stack
  cat $DEPLOY_ROOT/docker/stack-preamble.yml > $DEPLOY_ROOT/stack/stack-notificationmanager.yml

  #
  #  notificationmanager
  #

  for TUPLE in $NOTIFICATIONMANAGER_CONFIGURATION
  do
     export KEY=`echo $TUPLE | cut -d: -f1` 
     export HOST=`echo $TUPLE | cut -d: -f2` 
     export MONITORING_PORT=`echo $TUPLE | cut -d: -f3` 
     export DEBUG_PORT=`echo $TUPLE | cut -d: -f4` 
     export PLUGIN_NAME=`echo $TUPLE | cut -d: -f5` 
     export PLUGIN_CONFIGURATION=`echo $TUPLE | cut -d: -f6-`
     cat $DEPLOY_ROOT/docker/notificationmanager.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-notificationmanager.yml
     echo >> $DEPLOY_ROOT/stack/stack-notificationmanager.yml

  done 

  #
  #  postamble
  #

  cat $DEPLOY_ROOT/docker/stack-postamble.yml >> $DEPLOY_ROOT/stack/stack-notificationmanager.yml

fi


#########################################
#
#  construct stack -- reportmanager
#
#########################################

if [ "$REPORTMANAGER_ENABLED" = "true" ]; then

  #
  #  preamble
  #

  mkdir -p $DEPLOY_ROOT/stack
  cat $DEPLOY_ROOT/docker/stack-preamble.yml > $DEPLOY_ROOT/stack/stack-reportmanager.yml

  #
  #  reportmanager
  #

  for TUPLE in $REPORTMANAGER_CONFIGURATION
  do
     export KEY=`echo $TUPLE | cut -d: -f1`
     export HOST=`echo $TUPLE | cut -d: -f2`
     export MONITORING_PORT=`echo $TUPLE | cut -d: -f3`
     export DEBUG_PORT=`echo $TUPLE | cut -d: -f4`
     cat $DEPLOY_ROOT/docker/reportmanager.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-reportmanager.yml
     echo >> $DEPLOY_ROOT/stack/stack-reportmanager.yml
  done

  #
  #  postamble
  #

  cat $DEPLOY_ROOT/docker/stack-postamble.yml >> $DEPLOY_ROOT/stack/stack-reportmanager.yml

fi  

#########################################
#
#  construct stack -- reportscheduler
#
#########################################

if [ "$REPORTSCHEDULER_ENABLED" = "true" ]; then

  #
  #  preamble
  #

  mkdir -p $DEPLOY_ROOT/stack
  cat $DEPLOY_ROOT/docker/stack-preamble.yml > $DEPLOY_ROOT/stack/stack-reportscheduler.yml

  #
  #  reportscheduler
  #

  for TUPLE in $REPORTSCHEDULER_CONFIGURATION
  do
     export KEY=`echo $TUPLE | cut -d: -f1`
     export HOST=`echo $TUPLE | cut -d: -f2`
     export MONITORING_PORT=`echo $TUPLE | cut -d: -f3`
     export DEBUG_PORT=`echo $TUPLE | cut -d: -f4`
     cat $DEPLOY_ROOT/docker/reportscheduler.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-reportscheduler.yml
     echo >> $DEPLOY_ROOT/stack/stack-reportscheduler.yml
  done

  #
  #  postamble
  #

  cat $DEPLOY_ROOT/docker/stack-postamble.yml >> $DEPLOY_ROOT/stack/stack-reportscheduler.yml

fi  

#########################################
#
#  construct stack -- datacubemanager
#
#########################################

if [ "$DATACUBEMANAGER_ENABLED" = "true" ]; then

  #
  #  preamble
  #

  mkdir -p $DEPLOY_ROOT/stack
  cat $DEPLOY_ROOT/docker/stack-preamble.yml > $DEPLOY_ROOT/stack/stack-datacubemanager.yml

  #
  #  datacubemanager
  #

  cat $DEPLOY_ROOT/docker/datacubemanager.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-datacubemanager.yml
  echo >> $DEPLOY_ROOT/stack/stack-datacubemanager.yml

  #
  #  postamble
  #

  cat $DEPLOY_ROOT/docker/stack-postamble.yml >> $DEPLOY_ROOT/stack/stack-datacubemanager.yml

fi  

#########################################
#
#  construct stack -- elasticsearchmanager
#
#########################################

if [ "$ELASTICSEARCHMANAGER_ENABLED" = "true" ]; then

  #
  #  preamble
  #

  mkdir -p $DEPLOY_ROOT/stack
  cat $DEPLOY_ROOT/docker/stack-preamble.yml > $DEPLOY_ROOT/stack/stack-elasticsearchmanager.yml

  #
  #  elasticsearchmanager
  #

  cat $DEPLOY_ROOT/docker/elasticsearchmanager.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-elasticsearchmanager.yml
  echo >> $DEPLOY_ROOT/stack/stack-elasticsearchmanager.yml

  #
  #  postamble
  #

  cat $DEPLOY_ROOT/docker/stack-postamble.yml >> $DEPLOY_ROOT/stack/stack-elasticsearchmanager.yml

fi  

#########################
#
#  construct stack -- mysql
#
#########################

#
#  preamble
#

mkdir -p $DEPLOY_ROOT/stack
cat $DEPLOY_ROOT/docker/stack-preamble.yml > $DEPLOY_ROOT/stack/stack-mysql.yml

#
#  MySQL GUI
#

for TUPLE in $MYSQL_GUI_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export PORT=`echo $TUPLE | cut -d: -f4`
   cat $DEPLOY_ROOT/docker/mysql-gui.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-mysql.yml
   echo >> $DEPLOY_ROOT/stack/stack-mysql.yml
done

#
#  postamble
#

cat $DEPLOY_ROOT/docker/stack-postamble.yml >> $DEPLOY_ROOT/stack/stack-mysql.yml

#########################################
#
#  construct stack -- gui
#
#########################################

#
#  preamble
#

mkdir -p $DEPLOY_ROOT/stack
cat $DEPLOY_ROOT/docker/stack-preamble.yml > $DEPLOY_ROOT/stack/stack-gui.yml
#cat $DEPLOY_ROOT/docker/stack-preamble.yml > $DEPLOY_ROOT/stack/stack-gui-ssl-monitoring.yml

#
#  fwk-web
#

for TUPLE in $GUI_FWK_WEB_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   export HOST_EXTERNAL=`echo $TUPLE | cut -d: -f6`
   export GUI_FWK_WEB_SERVER_HOST_INTERNAL_IP=$HOST_IP
   export GUI_FWK_WEB_SERVER_PORT=$PORT
   if [[ "$GUI_HTTP_PROCOTOL" == "https" ]]; then
      if [ ${#GUI_FWK_API_SERVER_HOST_EXTERNAL} -ge 2 ]; then
         export GUI_FWK_API_FULL_PATH=$GUI_FWK_API_SERVER_HOST_EXTERNAL":"$GUI_HTTPS_PORT"/fwkapi"
         export FWKSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL"/fwk"
      else
         export GUI_FWK_API_FULL_PATH=$GUI_FWK_API_SERVER_HOST_EXTERNAL_IP"/fwkapi"
         export FWKSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL_IP"/fwk"
      fi
   else
      if [ ${#GUI_FWK_API_SERVER_HOST_EXTERNAL} -ge 2 ]; then
         export GUI_FWK_API_FULL_PATH=$GUI_FWK_API_SERVER_HOST_EXTERNAL"/fwkapi"
         export FWKSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL"/fwk"
      else
         export GUI_FWK_API_FULL_PATH=$GUI_FWK_API_SERVER_HOST_EXTERNAL_IP":"$GUI_FWK_API_SERVER_PORT"/api"
         export FWKSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL_IP":"$GUI_FWK_WEB_SERVER_PORT"/fwk"
      fi
   fi
   cat $DEPLOY_ROOT/docker/fwk-web.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-gui.yml
   echo >> $DEPLOY_ROOT/stack/stack-gui.yml
done

#
#  fwk-api
#

for TUPLE in $GUI_FWK_API_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   export GUI_FWK_API_SERVER_HOST_INTERNAL_IP=$HOST_IP
   export GUI_FWK_API_SERVER_PORT=$PORT
   cat $DEPLOY_ROOT/docker/fwk-api.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-gui.yml
   echo >> $DEPLOY_ROOT/stack/stack-gui.yml
done

#
#  fwkauth-api
#

for TUPLE in $GUI_FWK_AUTH_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   cat $DEPLOY_ROOT/docker/fwkauth-api.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-gui.yml
   echo >> $DEPLOY_ROOT/stack/stack-gui.yml
done

#
#  csr-web
#

for TUPLE in $GUI_CSR_WEB_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   export GUI_CSR_WEB_SERVER_HOST_INTERNAL_IP=$HOST_IP
   export GUI_CSR_WEB_SERVER_PORT=$PORT
   if [[ "$GUI_HTTP_PROCOTOL" == "https" ]]; then
      if [ ${#GUI_CSR_API_SERVER_HOST_EXTERNAL} -ge 2 ]; then
         export GUI_CSR_API_FULL_PATH=$GUI_CSR_API_SERVER_HOST_EXTERNAL":"$GUI_HTTPS_PORT"/csrapi"
         export CSRSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL"/csr"
      else
         export GUI_CSR_API_FULL_PATH=$GUI_CSR_API_SERVER_HOST_EXTERNAL_IP"/csrapi"
         export CSRSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL_IP"/csr"
      fi
   else
      if [ ${#GUI_CSR_API_SERVER_HOST_EXTERNAL} -ge 2 ]; then
         export GUI_CSR_API_FULL_PATH=$GUI_CSR_API_SERVER_HOST_EXTERNAL"/csrapi"
         export CSRSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL"/csr"
      else
         export GUI_CSR_API_FULL_PATH=$GUI_CSR_API_SERVER_HOST_EXTERNAL_IP":"$GUI_CSR_API_SERVER_PORT"/api"
         export CSRSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL_IP":"$GUI_CSR_WEB_SERVER_PORT"/csr"
      fi
   fi

   cat $DEPLOY_ROOT/docker/csr-web.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-gui.yml
   echo >> $DEPLOY_ROOT/stack/stack-gui.yml
done

#
#  csr-api
#

for TUPLE in $GUI_CSR_API_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   export GUI_CSR_API_SERVER_HOST_INTERNAL_IP=$HOST_IP
   export GUI_CSR_API_SERVER_PORT=$PORT
   cat $DEPLOY_ROOT/docker/csr-api.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-gui.yml
   echo >> $DEPLOY_ROOT/stack/stack-gui.yml
done

#
#  itm-web
#

for TUPLE in $GUI_ITM_WEB_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   export GUI_ITM_WEB_SERVER_HOST_INTERNAL_IP=$HOST_IP
   export GUI_ITM_WEB_SERVER_PORT=$PORT
   if [[ "$GUI_HTTP_PROCOTOL" == "https" ]]; then
      if [ ${#GUI_ITM_API_SERVER_HOST_EXTERNAL} -ge 2 ]; then
         export GUI_ITM_API_FULL_PATH=$GUI_ITM_API_SERVER_HOST_EXTERNAL":"$GUI_HTTPS_PORT"/itmapi"
         export ITMSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL"/itm"
      else
         export GUI_ITM_API_FULL_PATH=$GUI_ITM_API_SERVER_HOST_EXTERNAL_IP"/itmapi"
         export ITMSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL_IP"/itm"
      fi
   else
      if [ ${#GUI_ITM_API_SERVER_HOST_EXTERNAL} -ge 2 ]; then
         export GUI_ITM_API_FULL_PATH=$GUI_ITM_API_SERVER_HOST_EXTERNAL"/itmapi"
         export ITMSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL"/itm"
      else
         export GUI_ITM_API_FULL_PATH=$GUI_ITM_API_SERVER_HOST_EXTERNAL_IP":"$GUI_ITM_API_SERVER_PORT"/api"
         export ITMSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL_IP":"$GUI_ITM_WEB_SERVER_PORT"/itm"
      fi
   fi
   cat $DEPLOY_ROOT/docker/itm-web.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-gui.yml
   echo >> $DEPLOY_ROOT/stack/stack-gui.yml
done

#
#  itm-api
#

for TUPLE in $GUI_ITM_API_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   export GUI_ITM_API_SERVER_HOST_INTERNAL_IP=$HOST_IP
   export GUI_ITM_API_SERVER_PORT=$PORT
   cat $DEPLOY_ROOT/docker/itm-api.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-gui.yml
   echo >> $DEPLOY_ROOT/stack/stack-gui.yml
done

#
#  jmr-web
#

for TUPLE in $GUI_JMR_WEB_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   export GUI_JMR_WEB_SERVER_HOST_INTERNAL_IP=$HOST_IP
   export GUI_JMR_WEB_SERVER_PORT=$PORT
   if [[ "$GUI_HTTP_PROCOTOL" == "https" ]]; then
      if [ ${#GUI_JMR_API_SERVER_HOST_EXTERNAL} -ge 2 ]; then
         export GUI_JMR_API_FULL_PATH=$GUI_JMR_API_SERVER_HOST_EXTERNAL":"$GUI_HTTPS_PORT"/jmrapi"
         export JMRSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL"/jmr"
      else
         export GUI_JMR_API_FULL_PATH=$GUI_JMR_API_SERVER_HOST_EXTERNAL_IP"/jmrapi"
         export JMRSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL_IP"/jmr"
      fi
   else
      if [ ${#GUI_JMR_API_SERVER_HOST_EXTERNAL} -ge 2 ]; then
         export GUI_JMR_API_FULL_PATH=$GUI_JMR_API_SERVER_HOST_EXTERNAL"/jmrapi"
         export JMRSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL"/jmr"
      else
         export GUI_JMR_API_FULL_PATH=$GUI_JMR_API_SERVER_HOST_EXTERNAL_IP":"$GUI_JMR_API_SERVER_PORT"/api"
         export JMRSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL_IP":"$GUI_JMR_WEB_SERVER_PORT"/jmr"
      fi
   fi
   cat $DEPLOY_ROOT/docker/jmr-web.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-gui.yml
   echo >> $DEPLOY_ROOT/stack/stack-gui.yml
done

#
#  jmr-api
#

for TUPLE in $GUI_JMR_API_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   export GUI_JMR_API_SERVER_HOST_INTERNAL_IP=$HOST_IP
   export GUI_JMR_API_SERVER_PORT=$PORT
   cat $DEPLOY_ROOT/docker/jmr-api.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-gui.yml
   echo >> $DEPLOY_ROOT/stack/stack-gui.yml
done

#
#  opc-web
#

for TUPLE in $GUI_OPC_WEB_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   export GUI_OPC_WEB_SERVER_HOST_INTERNAL_IP=$HOST_IP
   export GUI_OPC_WEB_SERVER_PORT=$PORT   
   if [[ "$GUI_HTTP_PROCOTOL" == "https" ]]; then
      if [ ${#GUI_OPC_API_SERVER_HOST_EXTERNAL} -ge 2 ]; then
         export GUI_OPC_API_FULL_PATH=$GUI_OPC_API_SERVER_HOST_EXTERNAL":"$GUI_HTTPS_PORT"/opcapi"
         export OPCSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL"/opc"
      else
         export GUI_OPC_API_FULL_PATH=$GUI_OPC_API_SERVER_HOST_EXTERNAL_IP"/opcapi"
         export OPCSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL_IP"/opc"
      fi
   else
      if [ ${#GUI_OPC_API_SERVER_HOST_EXTERNAL} -ge 2 ]; then
         export GUI_OPC_API_FULL_PATH=$GUI_OPC_API_SERVER_HOST_EXTERNAL"/opcapi"
         export OPCSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL"/opc"
      else
         export GUI_OPC_API_FULL_PATH=$GUI_OPC_API_SERVER_HOST_EXTERNAL_IP":"$GUI_OPC_API_SERVER_PORT"/api"
         export OPCSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL_IP":"$GUI_OPC_WEB_SERVER_PORT"/opc"
      fi
   fi
   cat $DEPLOY_ROOT/docker/opc-web.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-gui.yml
   echo >> $DEPLOY_ROOT/stack/stack-gui.yml
done

#
#  opc-api
#

for TUPLE in $GUI_OPC_API_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   export GUI_OPC_API_SERVER_HOST_INTERNAL_IP=$HOST_IP
   export GUI_OPC_API_SERVER_PORT=$PORT
   cat $DEPLOY_ROOT/docker/opc-api.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-gui.yml
   echo >> $DEPLOY_ROOT/stack/stack-gui.yml
done

#
#  iar-web
#

for TUPLE in $GUI_IAR_WEB_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   export GUI_IAR_WEB_SERVER_HOST_INTERNAL_IP=$HOST_IP
   export GUI_IAR_WEB_SERVER_PORT=$PORT
   if [[ "$GUI_HTTP_PROCOTOL" == "https" ]]; then
      if [ ${#GUI_IAR_API_SERVER_HOST_EXTERNAL} -ge 2 ]; then
         export GUI_IAR_API_FULL_PATH=$GUI_IAR_API_SERVER_HOST_EXTERNAL":"$GUI_HTTPS_PORT"/iarapi"
         export IARSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL"/iar"
      else
         export GUI_IAR_API_FULL_PATH=$GUI_IAR_API_SERVER_HOST_EXTERNAL_IP"/iarapi"
         export IARSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL_IP"/iar"
      fi
   else
      if [ ${#GUI_IAR_API_SERVER_HOST_EXTERNAL} -ge 2 ]; then
         export GUI_IAR_API_FULL_PATH=$GUI_IAR_API_SERVER_HOST_EXTERNAL"/iarapi"
         export IARSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL"/iar"
      else
         export GUI_IAR_API_FULL_PATH=$GUI_IAR_API_SERVER_HOST_EXTERNAL_IP":"$GUI_IAR_API_SERVER_PORT"/api"
         export IARSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL_IP":"$GUI_IAR_WEB_SERVER_PORT"/iar"
      fi
   fi
   cat $DEPLOY_ROOT/docker/iar-web.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-gui.yml
   echo >> $DEPLOY_ROOT/stack/stack-gui.yml
done

#
#  iar-api
#

for TUPLE in $GUI_IAR_API_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   export GUI_IAR_API_SERVER_HOST_INTERNAL_IP=$HOST_IP
   export GUI_IAR_API_SERVER_PORT=$PORT
   cat $DEPLOY_ROOT/docker/iar-api.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-gui.yml
   echo >> $DEPLOY_ROOT/stack/stack-gui.yml
done

#
#  opr-web
#

for TUPLE in $GUI_OPR_WEB_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   export GUI_OPR_WEB_SERVER_HOST_INTERNAL_IP=$HOST_IP
   export GUI_OPR_WEB_SERVER_PORT=$PORT
   if [[ "$GUI_HTTP_PROCOTOL" == "https" ]]; then
      if [ ${#GUI_OPR_API_SERVER_HOST_EXTERNAL} -ge 2 ]; then
         export GUI_OPR_API_FULL_PATH=$GUI_OPR_API_SERVER_HOST_EXTERNAL":"$GUI_HTTPS_PORT"/oprapi"
         export OPRSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL"/opr"
      else
         export GUI_OPR_API_FULL_PATH=$GUI_OPR_API_SERVER_HOST_EXTERNAL_IP"/oprapi"
         export OPRSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL_IP"/opr"
      fi
   else
      if [ ${#GUI_OPR_API_SERVER_HOST_EXTERNAL} -ge 2 ]; then
         export GUI_OPR_API_FULL_PATH=$GUI_OPR_API_SERVER_HOST_EXTERNAL"/oprapi"
         export OPRSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL"/opr"
      else
         export GUI_OPR_API_FULL_PATH=$GUI_OPR_API_SERVER_HOST_EXTERNAL_IP":"$GUI_OPR_API_SERVER_PORT"/api"
         export OPRSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL_IP":"$GUI_OPR_WEB_SERVER_PORT"/opr"
      fi
   fi
   cat $DEPLOY_ROOT/docker/opr-web.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-gui.yml
   echo >> $DEPLOY_ROOT/stack/stack-gui.yml
done

#
#  opr-api
#

for TUPLE in $GUI_OPR_API_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`  
   export GUI_OPR_API_SERVER_HOST_INTERNAL_IP=$HOST_IP
   export GUI_OPR_API_SERVER_PORT=$PORT
   cat $DEPLOY_ROOT/docker/opr-api.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-gui.yml
   echo >> $DEPLOY_ROOT/stack/stack-gui.yml
done

#
#  stg-web
#

for TUPLE in $GUI_STG_WEB_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   export GUI_STG_WEB_SERVER_HOST_INTERNAL_IP=$HOST_IP
   export GUI_STG_WEB_SERVER_PORT=$PORT
   if [[ "$GUI_HTTP_PROCOTOL" == "https" ]]; then
      if [ ${#GUI_STG_API_SERVER_HOST_EXTERNAL} -ge 2 ]; then
         export GUI_STG_API_FULL_PATH=$GUI_STG_API_SERVER_HOST_EXTERNAL":"$GUI_HTTPS_PORT"/stgapi"
         export STGSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL"/stg"
      else
         export GUI_STG_API_FULL_PATH=$GUI_STG_API_SERVER_HOST_EXTERNAL_IP"/stgapi"
         export STGSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL_IP"/stg"
      fi
   else
      if [ ${#GUI_STG_API_SERVER_HOST_EXTERNAL} -ge 2 ]; then
         export GUI_STG_API_FULL_PATH=$GUI_STG_API_SERVER_HOST_EXTERNAL"/stgapi"
         export STGSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL"/stg"
      else
         export GUI_STG_API_FULL_PATH=$GUI_STG_API_SERVER_HOST_EXTERNAL_IP":"$GUI_STG_API_SERVER_PORT"/api"
         export STGSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL_IP":"$GUI_STG_WEB_SERVER_PORT"/stg"
      fi
   fi
   cat $DEPLOY_ROOT/docker/stg-web.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-gui.yml
   echo >> $DEPLOY_ROOT/stack/stack-gui.yml
done

#
#  stg-api
#

for TUPLE in $GUI_STG_API_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   export GUI_STG_API_SERVER_HOST_INTERNAL_IP=$HOST_IP
   export GUI_STG_API_SERVER_PORT=$PORT
   cat $DEPLOY_ROOT/docker/stg-api.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-gui.yml
   echo >> $DEPLOY_ROOT/stack/stack-gui.yml
done

#
#  sbm-web
#

for TUPLE in $GUI_SBM_WEB_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   export GUI_SBM_WEB_SERVER_HOST_INTERNAL_IP=$HOST_IP
   export GUI_SBM_WEB_SERVER_PORT=$PORT
   if [[ "$GUI_HTTP_PROCOTOL" == "https" ]]; then
      if [ ${#GUI_SBM_API_SERVER_HOST_EXTERNAL} -ge 2 ]; then
         export GUI_SBM_API_FULL_PATH=$GUI_SBM_API_SERVER_HOST_EXTERNAL":"$GUI_HTTPS_PORT"/ucgapi"
         export SBMSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL"/ucg"
      else
         export GUI_SBM_API_FULL_PATH=$GUI_SBM_API_SERVER_HOST_EXTERNAL_IP"/ucgapi"
         export SBMSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL_IP"/ucg"
      fi
   else
      if [ ${#GUI_SBM_API_SERVER_HOST_EXTERNAL} -ge 2 ]; then
         export GUI_SBM_API_FULL_PATH=$GUI_SBM_API_SERVER_HOST_EXTERNAL"/ucgapi"
         export SBMSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL"/ucg"
      else
         export GUI_SBM_API_FULL_PATH=$GUI_SBM_API_SERVER_HOST_EXTERNAL_IP":"$GUI_SBM_API_SERVER_PORT"/api"
         export SBMSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL_IP":"$GUI_SBM_WEB_SERVER_PORT"/ucg"
      fi
   fi
   cat $DEPLOY_ROOT/docker/sbm-web.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-gui.yml
   echo >> $DEPLOY_ROOT/stack/stack-gui.yml
done

#
#  sbm-api
#

for TUPLE in $GUI_SBM_API_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   export GUI_SBM_API_SERVER_HOST_INTERNAL_IP=$HOST_IP
   export GUI_SBM_API_SERVER_PORT=$PORT
   cat $DEPLOY_ROOT/docker/sbm-api.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-gui.yml
   echo >> $DEPLOY_ROOT/stack/stack-gui.yml
done

#
#  lpm-web
#

for TUPLE in $GUI_LPM_WEB_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   export HOST_EXTERNAL=`echo $TUPLE | cut -d: -f6`
   export GUI_LPM_WEB_SERVER_HOST_INTERNAL_IP=$HOST_IP
   export GUI_LPM_WEB_SERVER_PORT=$PORT
   if [[ "$GUI_HTTP_PROCOTOL" == "https" ]]; then
      if [ ${#GUI_LPM_API_SERVER_HOST_EXTERNAL} -ge 2 ]; then
         export GUI_LPM_API_FULL_PATH=$GUI_LPM_API_SERVER_HOST_EXTERNAL":"$GUI_HTTPS_PORT"/lpmapi"
         export LPMSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL"/lpm"
      else
         export GUI_LPM_API_FULL_PATH=$GUI_LPM_API_SERVER_HOST_EXTERNAL_IP"/lpmapi"
         export LPMSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL_IP"/lpm"
      fi
   else
      if [ ${#GUI_LPM_API_SERVER_HOST_EXTERNAL} -ge 2 ]; then
         export GUI_LPM_API_FULL_PATH=$GUI_LPM_API_SERVER_HOST_EXTERNAL"/lpmapi"
         export LPMSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL"/lpm"
      else
         export GUI_LPM_API_FULL_PATH=$GUI_LPM_API_SERVER_HOST_EXTERNAL_IP":"$GUI_LPM_API_SERVER_PORT"/api"
         export LPMSETTINGS_WEBCALLBACKURL=$GUI_HTTP_PROCOTOL"://"$HOST_EXTERNAL_IP":"$GUI_LPM_WEB_SERVER_PORT"/lpm"
      fi
   fi
   cat $DEPLOY_ROOT/docker/lpm-web.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-gui.yml
   echo >> $DEPLOY_ROOT/stack/stack-gui.yml
done

#
#  lpm-api
#

for TUPLE in $GUI_LPM_API_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   export GUI_LPM_API_SERVER_HOST_INTERNAL_IP=$HOST_IP
   export GUI_LPM_API_SERVER_PORT=$PORT
   cat $DEPLOY_ROOT/docker/lpm-api.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-gui.yml
   echo >> $DEPLOY_ROOT/stack/stack-gui.yml
done


#
#  doc-web
#

for TUPLE in $GUI_DOC_WEB_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export HOST_EXTERNAL_IP=`echo $TUPLE | cut -d: -f4`
   export PORT=`echo $TUPLE | cut -d: -f5`
   export HOST_EXTERNAL=`echo $TUPLE | cut -d: -f6`
   export GUI_DOC_WEB_SERVER_HOST_INTERNAL_IP=$HOST_IP
   export GUI_DOC_WEB_SERVER_PORT=$PORT  
   cat $DEPLOY_ROOT/docker/doc-web.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-gui.yml
   echo >> $DEPLOY_ROOT/stack/stack-gui.yml
done

#
#  gui-audit
#

for TUPLE in $GUI_AUDIT_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   cat $DEPLOY_ROOT/docker/gui-audit.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-gui.yml
   echo >> $DEPLOY_ROOT/stack/stack-gui.yml
done

#
#  gui-links
#

for TUPLE in $GUI_LINKS_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   cat $DEPLOY_ROOT/docker/gui-links.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-gui.yml
   echo >> $DEPLOY_ROOT/stack/stack-gui.yml
done

#
#  build stack-gui-ssl-monitoring.yml     gui-onssl
#

for TUPLE in $GUI_ONSSL_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   cat $DEPLOY_ROOT/docker/gui-onssl.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-gui.yml
   echo >> $DEPLOY_ROOT/stack/stack-gui.yml
done


#
# configs for GUI
#

cat $DEPLOY_ROOT/docker/fwk-config.yml >> $DEPLOY_ROOT/stack/stack-gui.yml

#
#  postamble
#

cat $DEPLOY_ROOT/docker/stack-postamble.yml >> $DEPLOY_ROOT/stack/stack-gui.yml

#cat $DEPLOY_ROOT/docker/stack-postamble.yml >> $DEPLOY_ROOT/stack/stack-gui-ssl-monitoring.yml

###########################################################################
#
#  construct stack -- Upgrade - preamble to a generic upgrade stack for NGLM
#
###########################################################################

#
#  preamble
#

mkdir -p $DEPLOY_ROOT/stack
cat $DEPLOY_ROOT/docker/stack-preamble.yml > $DEPLOY_ROOT/stack/stack-upgrade.yml

for TUPLE in $NGLM_UPGRADE_CONFIGURATION
do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   cat $DEPLOY_ROOT/docker/upgrade.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-upgrade.yml
   echo >> $DEPLOY_ROOT/stack/stack-upgrade.yml
done

#
#  postamble
#

cat $DEPLOY_ROOT/docker/stack-postamble.yml >> $DEPLOY_ROOT/stack/stack-upgrade.yml

#########################################
#
#  construct stack -- emulators(if necessary)
#
#########################################

if [ "$FAKEEMULATORS_ENABLED" = "true" ]; then

  #
  #  preamble
  #

  mkdir -p $DEPLOY_ROOT/stack
  cat $DEPLOY_ROOT/docker/stack-preamble.yml > $DEPLOY_ROOT/stack/stack-fake.yml

  #
  #  fake smsc
  #

  for TUPLE in $FAKE_SMSC_CONFIGURATION
  do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export HOST_IP=`echo $TUPLE | cut -d: -f3`
   export SMPP_PORT=`echo $TUPLE | cut -d: -f4`
   export HTTP_PORT=`echo $TUPLE | cut -d: -f5`
   cat $DEPLOY_ROOT/docker/fakesmsc.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-fake.yml
  done

  #
  #  fake smtp
  #

  for TUPLE in $FAKE_SMTP_CONFIGURATION
  do
   export KEY=`echo $TUPLE | cut -d: -f1`
   export HOST=`echo $TUPLE | cut -d: -f2`
   export SMTP_PORT=`echo $TUPLE | cut -d: -f3`
   cat $DEPLOY_ROOT/docker/fakesmtp.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-fake.yml
  done
  
  #
  #  fake in
  #

  for TUPLE in $FAKE_IN_IN_SERVERS
  do
    export KEY=`echo $TUPLE | cut -d: -f1`
    export HOST=`echo $TUPLE | cut -d: -f2`
    export PORT=`echo $TUPLE | cut -d: -f3`
    export DETERMINISTIC=`echo $TUPLE | cut -d: -f4`
    declare FAKE_IN_IN_SERVERS_$KEY="$HOST:$PORT"
    varname=FAKE_IN_IN_SERVERS_$KEY
    cat $DEPLOY_ROOT/docker/fakein.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-fake.yml
  done
  
  #
  #  postamble
  #

  cat $DEPLOY_ROOT/docker/stack-postamble.yml >> $DEPLOY_ROOT/stack/stack-fake.yml
  
fi

#########################################
#
#  construct stack -- extractmanager
#
#########################################

if [ "$EXTRACTMANAGER_ENABLED" = "true" ]; then

  #
  #  preamble
  #

  mkdir -p $DEPLOY_ROOT/stack
  cat $DEPLOY_ROOT/docker/stack-preamble.yml > $DEPLOY_ROOT/stack/stack-extractmanager.yml

  #
  #  extractmanager
  #

  for TUPLE in $EXTRACTMANAGER_CONFIGURATION
  do
     export KEY=`echo $TUPLE | cut -d: -f1`
     export HOST=`echo $TUPLE | cut -d: -f2`
     export MONITORING_PORT=`echo $TUPLE | cut -d: -f3`
     export DEBUG_PORT=`echo $TUPLE | cut -d: -f4`
     cat $DEPLOY_ROOT/docker/extractmanager.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-extractmanager.yml
     echo >> $DEPLOY_ROOT/stack/stack-extractmanager.yml
  done

  #
  #  postamble
  #

  cat $DEPLOY_ROOT/docker/stack-postamble.yml >> $DEPLOY_ROOT/stack/stack-extractmanager.yml

fi


#########################################
#
#  construct stack -- backupmanager
#
#########################################

if [ "$BACKUPMANAGER_ENABLED" = "true" ]; then

  #
  #  preamble
  #

  mkdir -p $DEPLOY_ROOT/stack
  cat $DEPLOY_ROOT/docker/stack-preamble.yml > $DEPLOY_ROOT/stack/stack-backupmanager.yml

  #
  #  backupmanager
  #

  for TUPLE in $BACKUPMANAGER_CONFIGURATION
  do
     export KEY=`echo $TUPLE | cut -d: -f1`
     export HOST=`echo $TUPLE | cut -d: -f2`
     export MONITORING_PORT=`echo $TUPLE | cut -d: -f3`
     export DEBUG_PORT=`echo $TUPLE | cut -d: -f4`
     cat $DEPLOY_ROOT/docker/backupmanager.yml | perl -e 'while ( $line=<STDIN> ) { $line=~s/<_([A-Z_0-9]+)_>/$ENV{$1}/g; print $line; }' | sed 's/\\n/\n/g' | sed 's/^/  /g' >> $DEPLOY_ROOT/stack/stack-backupmanager.yml
     echo >> $DEPLOY_ROOT/stack/stack-backupmanager.yml
  done

  #
  #  postamble
  #

  cat $DEPLOY_ROOT/docker/stack-postamble.yml >> $DEPLOY_ROOT/stack/stack-backupmanager.yml

fi


