#################################################################################
#
#  evolution-setup-elasticsearch
#
#################################################################################

# -------------------------------------------------------------------------------
#
# default
#
# -------------------------------------------------------------------------------
#
#  Root template.
# Those settings will be applied by default to all indexes
# Order is intentionally set to -10 (to be taken into account before every other templates).
#
if [ "${env.USE_REGRESSION}" = "1" ]
then
# speed up refresh_interval for tests : 5s instead of 30s
prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/root -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["*"],
  "order": -10,
  "settings" : {
    "index" : {
      "number_of_shards" : "Deployment.getElasticsearchDefaultShards()",
      "number_of_replicas" : "Deployment.getElasticsearchDefaultReplicas()",
      "refresh_interval" : "5s",
      "translog" : { 
        "durability" : "async", 
        "sync_interval" : "10s" 
      }
    }
  },
  "mappings" : {
    "_meta": { "root" : { "version": Deployment.getElasticsearchRootTemplateVersion() } },
    "dynamic_templates": [ {
      "strings_as_keywords": {
        "match_mapping_type": "string",
        "mapping": { "type": "keyword" }
      }
    }, {
      "numerics_as_integers": {
        "match_mapping_type": "long",
        "mapping": { "type": "integer" }
      }
    }]
  }
}'
else
prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/root -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["*"],
  "order": -10,
  "settings" : {
    "index" : {
      "number_of_shards" : "Deployment.getElasticsearchDefaultShards()",
      "number_of_replicas" : "Deployment.getElasticsearchDefaultReplicas()",
      "refresh_interval" : "30s",
      "translog" : { 
        "durability" : "async", 
        "sync_interval" : "10s" 
      }
    }
  },
  "mappings" : {
    "_meta": { "root" : { "version": Deployment.getElasticsearchRootTemplateVersion() } },
    "dynamic_templates": [ {
      "strings_as_keywords": {
        "match_mapping_type": "string",
        "mapping": { "type": "keyword" }
      }
    }, {
      "numerics_as_integers": {
        "match_mapping_type": "long",
        "mapping": { "type": "integer" }
      }
    }]
  }
}'	
fi
echo

# -------------------------------------------------------------------------------
#
# subscriberprofile & snapshots
#
# -------------------------------------------------------------------------------
#
#  manually create subscriberprofile template
#   - this template will be used by the subscriberprofile index, and also subscriberprofile snapshots
#  Order is set to -1 to be taken into account before subscriberprofile_snapshot
#
prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/subscriberprofile -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["subscriberprofile*"],
  "order": -1,
  "settings" : {
    "index" : {
      "number_of_shards" : "Deployment.getElasticsearchSubscriberprofileShards()",
      "number_of_replicas" : "Deployment.getElasticsearchSubscriberprofileReplicas()"
    }
  },
  "mappings" : {
    "_meta": { "subscriberprofile" : { "version": Deployment.getElasticsearchSubscriberprofileTemplateVersion() } },
    "properties" : {
      "subscriberID"                        : { "type" : "keyword" },
      "tenantID"                            : { "type" : "integer" },
      "evaluationDate"                      : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" },
      "evolutionSubscriberStatus"           : { "type" : "keyword" },
      "previousEvolutionSubscriberStatus"   : { "type" : "keyword" },
      "evolutionSubscriberStatusChangeDate" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" },
      "universalControlGroup"               : { "type" : "boolean" },
      "universalControlGroupPrevious"       : { "type" : "boolean" },
      "universalControlGroupChangeDate"     : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" },
      "universalControlGroupHistoryAuditInfo":{ "type" : "keyword" },
      "language"                            : { "type" : "keyword" },
      "segments"                            : { "type" : "keyword" },
      "exclusionInclusionList"              : { "type" : "keyword" },
      "targets"                             : { "type" : "keyword" },
      "lastUpdateDate"                      : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" },
      "pointFluctuations"                   : { "type" : "object"  },
      "scoreFluctuations"                   : { "type" : "object"  },
      "progressionFluctuations"             : { "type" : "object"  },
      "subscriberJourneys"                  : { "type" : "nested"  },
      "tokens"                              : { "type" : "nested",
        "properties" : {
          "creationDate"       : { "type" : "long" },
          "expirationDate"     : { "type" : "long" },
          "redeemedDate"       : { "type" : "long" },
          "lastAllocationDate" : { "type" : "long" }
        }
      },
      "vouchers"                            : { "type" : "nested",
        "properties" : {
          "vouchers"        : { "type": "nested", "properties": { "voucherExpiryDate" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" }, "voucherDeliveryDate" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" } } }
        }
      },
      "loyaltyPrograms"                     : { "type" : "nested",
        "properties" : {
          "loyaltyProgramEnrollmentDate" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" },
          "loyaltyProgramExitDate"       : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" },
          "tierUpdateDate"               : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" },
          "loyaltyProgramEpoch"          : { "type" : "long" },
          "rewardTodayRedeemer"          : { "type" : "boolean" },
          "rewardYesterdayRedeemer"      : { "type" : "boolean" },
          "levelUpdateDate"              : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ"},
          "stepUpdateDate"               : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ"}
        }
      },
       "complexFields"                   : { "type" : "nested",
        "properties" : {
          "complexObjectName" : 	{ "type" : "keyword"},
          "complexObjectID" : 		{ "type" : "keyword"},
          "complexObjectDisplay" : 	{ "type" : "keyword"}
        }
      },
      "pointBalances"                       : { "type": "nested",
        "properties" : {
          "earliestExpirationDate" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" },
          "expirationDates"        : { "type": "nested", "properties": { "date" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" } } }
         }
      },
      "badges"                            : { "type" : "nested",
        "properties" : {
          "badges"        : { "type": "nested", "properties": { "badgeAwardDate" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" }, "badgeRemoveDate" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" } } }
        }
      }
    }
  }
}'
echo

#
#  create a cleaning policy for subscriberprofile snapshots
#
prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_opendistro/_ism/policies/subscriberprofile_snapshot_policy -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "policy": {
    "description": "hot delete workflow for subscriber snapshot",
    "default_state": "hot",
    "schema_version": 1,
    "states": [
      {
        "name": "hot",
        "actions": [],
        "transitions": [
          {
            "state_name": "delete",
            "conditions": {
              "min_index_age": "Deployment.getElasticsearchRetentionDaysSnapshots()d"
            }
          }
        ]
      },
      {
        "name": "delete",
        "actions": [
          {
            "delete": {}
          }
        ]
      }
    ]
  }
}'
echo

#
#  override subscriberprofile template for snapshots ONLY with cleaning policy
#
prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/subscriberprofile_snapshot -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["subscriberprofile_snapshot-*"],
  "settings" : {
    "index" : {
      "number_of_shards" : "Deployment.getElasticsearchSnapshotShards()",
      "number_of_replicas" : "Deployment.getElasticsearchSnapshotReplicas()"
    },
    "opendistro.index_state_management.policy_id": "subscriberprofile_snapshot_policy"
  }
}'
echo

# -------------------------------------------------------------------------------
#
# edr, odr, bdr, mdr, token
#
# -------------------------------------------------------------------------------

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_opendistro/_ism/policies/edr_policy -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "policy": {
    "description": "hot delete workflow for edr",
    "default_state": "hot",
    "schema_version": 1,
    "states": [
      {
        "name": "hot",
        "actions": [],
        "transitions": [
          {
            "state_name": "delete",
            "conditions": {
              "min_index_age": "Deployment.getElasticsearchRetentionDaysEDR()d"
            }
          }
        ]
      },
      {
        "name": "delete",
        "actions": [
          {
            "delete": {}
          }
        ]
      }
    ]
  }
}'
echo

#
#  create a cleaning policy for bdr
#
prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_opendistro/_ism/policies/bdr_policy -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "policy": {
    "description": "hot delete workflow for bdr",
    "default_state": "hot",
    "schema_version": 1,
    "states": [
      {
        "name": "hot",
        "actions": [],
        "transitions": [
          {
            "state_name": "delete",
            "conditions": {
              "min_index_age": "Deployment.getElasticsearchRetentionDaysBDR()d"
            }
          }
        ]
      },
      {
        "name": "delete",
        "actions": [
          {
            "delete": {}
          }
        ]
      }
    ]
  }
}'
echo

#
#  manually create bdr template
#
prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/bdr -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["detailedrecords_bonuses-*"],
  "settings" : {
    "opendistro.index_state_management.policy_id": "bdr_policy"
  },
  "mappings" : {
    "_meta": { "bdr" : { "version": Deployment.getElasticsearchBdrTemplateVersion() } },
    "properties" : {
      "subscriberID" : { "type" : "keyword" },
      "tenantID" : { "type" : "integer" },
      "providerID" : { "type" : "keyword" },
      "eventID" : { "type" : "keyword" },
      "deliveryRequestID" : { "type" : "keyword" },
      "deliverableID" : { "type" : "keyword" },
      "eventDatetime" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ"},
      "deliverableExpirationDate" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" },
      "creationDate" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ"},
      "deliverableQty" : { "type" : "integer", "index" : "false" },
      "operation" : { "type" : "keyword" },
      "moduleID" : { "type" : "keyword" },
      "featureID" : { "type" : "keyword" },
      "origin" : { "type" : "keyword" },
      "returnCode" : { "type" : "integer" },
      "deliveryStatus" : { "type" : "keyword" },
      "returnCodeDetails" : { "type" : "keyword", "index" : "false" }
    }
  }
}'
echo

#
#  create a cleaning policy for tokens
#
prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_opendistro/_ism/policies/token_policy -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "policy": {
    "description": "hot delete workflow for token",
    "default_state": "hot",
    "schema_version": 1,
    "states": [
      {
        "name": "hot",
        "actions": [],
        "transitions": [
          {
            "state_name": "delete",
            "conditions": {
              "min_index_age": "Deployment.getElasticsearchRetentionDaysTokens()d"
            }
          }
        ]
      },
      {
        "name": "delete",
        "actions": [
          {
            "delete": {}
          }
        ]
      }
    ]
  }
}'
echo

#
#  manually create token template
#
prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/token -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["detailedrecords_tokens-*"],
  "settings" : {
    "opendistro.index_state_management.policy_id": "token_policy"
  },
  "mappings" : {
    "_meta": { "token" : { "version": Deployment.getElasticsearchTokenTemplateVersion() } },
    "properties" : {
      "subscriberID"  : { "type" : "keyword" },
      "tenantID"      : { "type" : "integer" },
      "tokenCode"     : { "type" : "keyword" },
      "action"        : { "type" : "keyword" },
      "eventDatetime" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ"},
      "eventID"       : { "type" : "keyword" },
      "returnCode"    : { "type" : "keyword" },
      "origin"        : { "type" : "keyword" },
      "acceptedOfferID"  : { "type" : "keyword" },
      "presentedOffersIDs" : { "type" : "keyword" }
    }
  }
}'
echo

#
#  create a cleaning policy for odr
#
prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_opendistro/_ism/policies/odr_policy -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "policy": {
    "description": "hot delete workflow for odr",
    "default_state": "hot",
    "schema_version": 1,
    "states": [
      {
        "name": "hot",
        "actions": [],
        "transitions": [
          {
            "state_name": "delete",
            "conditions": {
              "min_index_age": "Deployment.getElasticsearchRetentionDaysODR()d"
            }
          }
        ]
      },
      {
        "name": "delete",
        "actions": [
          {
            "delete": {}
          }
        ]
      }
    ]
  }
}'
echo

#
#  manually create odr template
#
prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/odr -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["detailedrecords_offers-*"],
  "settings" : {
    "opendistro.index_state_management.policy_id": "odr_policy"
  },
  "mappings" : {
    "_meta": { "odr" : { "version": Deployment.getElasticsearchOdrTemplateVersion() } },
    "properties" : {
      "subscriberID" : { "type" : "keyword" },
      "tenantID" : { "type" : "integer" },
      "eventDatetime" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ"},
      "creationDate" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ"},
      "deliveryRequestID" : { "type" : "keyword" },
      "eventID" : { "type" : "keyword" },
      "offerID" : { "type" : "keyword" },
      "offerQty" : { "type" : "integer", "index" : "false" },
      "salesChannelID" : { "type" : "keyword" },
      "offerPrice" : { "type" : "integer", "index" : "false" },
      "meanOfPayment" : { "type" : "keyword", "index" : "false" },
      "offerStock" : { "type" : "integer", "index" : "false" },
      "offerContent" : { "type" : "keyword", "index" : "false" },
      "moduleID" : { "type" : "keyword" },
      "featureID" : { "type" : "keyword" },
      "origin" : { "type" : "keyword" },
      "returnCode" : { "type" : "integer" },
      "deliveryStatus" : { "type" : "keyword" },
      "returnCodeDetails" : { "type" : "keyword", "index" : "false" },
      "voucherCode" : { "type" : "keyword" },
      "voucherPartnerID" : { "type" : "keyword" }
    }
  }
}'
echo

#
#  create a cleaning policy for VDR
#
prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_opendistro/_ism/policies/vdr_policy -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "policy": {
    "description": "hot delete workflow for vdr",
    "default_state": "hot",
    "schema_version": 1,
    "states": [
      {
        "name": "hot",
        "actions": [],
        "transitions": [
          {
            "state_name": "delete",
            "conditions": {
              "min_index_age": "Deployment.getElasticsearchRetentionDaysVDR()d"
            }
          }
        ]
      },
      {
        "name": "delete",
        "actions": [
          {
            "delete": {}
          }
        ]
      }
    ]
  }
}'
echo

#
#  manually create vdr template
#
prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/vdr -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["detailedrecords_vouchers-*"],
  "settings" : {
    "opendistro.index_state_management.policy_id": "vdr_policy"
  },
  "mappings" : {
    "_meta": { "vdr" : { "version": Deployment.getElasticsearchVdrTemplateVersion() } },
    "properties" : {
      "subscriberID" : { "type" : "keyword" },
      "tenantID" : { "type" : "integer" },
      "eventDatetime" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ"},
      "eventID" : { "type" : "keyword" },
      "moduleID" : { "type" : "keyword" },
      "featureID" : { "type" : "keyword" },
      "origin" : { "type" : "keyword" },
      "returnStatus" : { "type" : "keyword" },
      "voucherCode" : { "type" : "keyword" },
      "voucherID" : { "type" : "keyword" },
      "returnCode" : { "type" : "integer" },
      "expiryDate" : { "type" : "keyword" },
      "deliveryRequestID" : { "type" : "keyword" }
    }
  }
}'
echo

#
#  create a cleaning policy for BGDR
#
prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_opendistro/_ism/policies/bgdr_policy -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "policy": {
    "description": "hot delete workflow for bgdr",
    "default_state": "hot",
    "schema_version": 1,
    "states": [
      {
        "name": "hot",
        "actions": [],
        "transitions": [
          {
            "state_name": "delete",
            "conditions": {
              "min_index_age": "Deployment.getElasticsearchRetentionDaysBGDR()d"
            }
          }
        ]
      },
      {
        "name": "delete",
        "actions": [
          {
            "delete": {}
          }
        ]
      }
    ]
  }
}'
echo

#
#  manually create bgdr template
#
prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/bgdr -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["detailedrecords_badges-*"],
  "settings" : {
    "opendistro.index_state_management.policy_id": "bgdr_policy"
  },
  "mappings" : {
    "_meta": { "bgdr" : { "version": Deployment.getElasticsearchBGdrTemplateVersion() } },
    "properties" : {
      "subscriberID"		: { "type" : "keyword" },
      "deliveryRequestID"	: { "type" : "keyword" },
      "tenantID" 			: { "type" : "integer" },
      "eventDatetime" 		: { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ"},
      "eventID" 			: { "type" : "keyword" },     
      "moduleID" 			: { "type" : "keyword" },
      "featureID" 			: { "type" : "keyword" },
      "origin" 				: { "type" : "keyword" },
      "returnStatus" 		: { "type" : "keyword" },
      "badgeID" 			: { "type" : "keyword" },
      "action" 				: { "type" : "keyword" },
      "returnCode" 			: { "type" : "keyword" }
    }
  }
}'
echo

#
#  create a cleaning policy for mdr
#
prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_opendistro/_ism/policies/mdr_policy -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "policy": {
    "description": "hot delete workflow for mdr",
    "default_state": "hot",
    "schema_version": 1,
    "states": [
      {
        "name": "hot",
        "actions": [],
        "transitions": [
          {
            "state_name": "delete",
            "conditions": {
              "min_index_age": "Deployment.getElasticsearchRetentionDaysMDR()d"
            }
          }
        ]
      },
      {
        "name": "delete",
        "actions": [
          {
            "delete": {}
          }
        ]
      }
    ]
  }
}'
echo

#
#  manually create mdr template
#
prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/mdr -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["detailedrecords_messages-*"],
  "settings" : {
    "opendistro.index_state_management.policy_id": "mdr_policy"
  },
  "mappings" : {
    "_meta": { "mdr" : { "version": Deployment.getElasticsearchMdrTemplateVersion() } },
    "properties" : {
      "subscriberID" : { "type" : "keyword" },
      "tenantID" : { "type" : "integer" },
      "eventID" : { "type" : "keyword" },
      "deliveryRequestID" : { "type" : "keyword" },
      "creationDate" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ"},
      "deliveryDate" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ"},
      "messageID" : { "type" : "keyword" },
      "destination" : { "type" : "keyword" }, 
      "moduleID" : { "type" : "keyword" },
      "featureID" : { "type" : "keyword" },
      "origin" : { "type" : "keyword" },
      "returnCode" : { "type" : "integer" },
      "deliveryStatus" : { "type" : "keyword" },
      "returnCodeDetails" : { "type" : "keyword", "index" : "false" },
      "originatingDeliveryRequestID" : { "type" : "keyword" },
      "contactType" : { "type" : "keyword" },
      "noOfParts" : { "type" : "keyword" }
    }
  }
}'
echo

# -------------------------------------------------------------------------------
#
# journeystatistic & workflowarchive
#
# -------------------------------------------------------------------------------
#
#  manually create journeystatistic index
#
prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/journeystatistic -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["journeystatistic*"],
  "mappings" : {
    "_meta": { "journeystatistic" : { "version": Deployment.getElasticsearcJourneystatisticTemplateVersion() } },
    "properties" : {
      "journeyInstanceID" : { "type" : "keyword" },
      "journeyID" : { "type" : "keyword" },
      "subscriberID" : { "type" : "keyword" },
      "tenantID" : { "type" : "integer" },
      "transitionDate" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" },
      "nodeID" : { "type" : "keyword" },
      "nodeHistory" : { "type" : "keyword" },
      "statusHistory" : { "type" : "keyword" },
      "rewardHistory" : { "type" : "keyword" },
      "deliveryRequestID" : { "type" : "keyword" },
      "sample" : { "type" : "keyword" },
      "statusNotified" : { "type" : "boolean" },
      "statusConverted" : { "type" : "boolean" },
      "conversionCount" : { "type" : "long" },
      "lastConversionDate" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" },
      "statusTargetGroup" : { "type" : "boolean" },
      "statusControlGroup" : { "type" : "boolean" },
      "statusUniversalControlGroup" : { "type" : "boolean" },
      "journeyComplete" : { "type" : "boolean" },
      "status" : { "type" : "keyword" },
      "journeyExitDate" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" }
    }
  }
}'
echo

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/workflowarchive -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["workflowarchive*"],
  "mappings" : {
    "_meta": { "workflowarchive" : { "version": Deployment.getElasticsearcWorkflowarchiveTemplateVersion() } },
    "properties" : {
      "journeyID" : { "type" : "keyword" },
      "tenantID" : { "type" : "integer" },
      "nodeID" : { "type" : "keyword" },
      "status" : { "type" : "keyword" },
      "count" : { "type" : "long" }
    }
  }
}'
echo

# -------------------------------------------------------------------------------
#
# reg_criteria, regr_counter (for test environment ONLY)
#
# -------------------------------------------------------------------------------
if [ "${env.USE_REGRESSION}" = "1" ]
then
  #
  #  manually create regr_criteria index
  #
  prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/regr_criteria -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
  {
    "index_patterns": ["regr_criteria"],
    "mappings" : {
      "properties" : {
        "subscriberID" : { "type" : "keyword" },
        "offerID" : { "type" : "keyword" },
        "eligible" : { "type" : "keyword" },
        "evaluationDate" : { "type" : "date" }
      }
    }
  }'
  echo

  #
  #  manually create regr_counter index
  #
  prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/regr_counter -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
  {
    "index_patterns": ["regr_counter"],
    "mappings" : {
      "properties" : {
        "count" : { "type" : "long" }
      }
    }
  }'

  prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/regr_counter/_doc/1 -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
  {
    "count" : 100
  }'
  echo
fi

# -------------------------------------------------------------------------------
#
# datacubes
#
# -------------------------------------------------------------------------------
prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/datacube_subscriberprofile -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["t*_datacube_subscriberprofile"],
  "mappings" : {
    "_meta": { "datacube_subscriberprofile" : { "version": Deployment.getElasticsearchDatacubeSubscriberprofileTemplateVersion() } },
    "properties" : {
      "timestamp" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" },
      "period" : { "type" : "long" },
      "filter.tenantID" : { "type" : "integer" },
      "count" : { "type" : "integer" }
    }
  }
}'
echo

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/datacube_loyaltyprogramshistory -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["t*_datacube_loyaltyprogramshistory"],
  "mappings" : {
    "_meta": { "datacube_loyaltyprogramshistory" : { "version": Deployment.getElasticsearchDatacubeLoyaltyprogramshistoryTemplateVersion() } },
    "properties" : {
      "timestamp" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" },
      "period" : { "type" : "long" },
      "filter.tenantID" : { "type" : "integer" },
      "filter.loyaltyProgram" : { "type" : "keyword" },
      "filter.tier" : { "type" : "keyword" },
      "filter.evolutionSubscriberStatus" : { "type" : "keyword" },
      "filter.redeemer" : { "type" : "boolean" },
      "count" : { "type" : "integer" },
      "metric.rewards.redeemed" : { "type" : "integer" },
      "metric.rewards.earned" : { "type" : "integer" },
      "metric.rewards.expired" : { "type" : "integer" },
      "metric.rewards.redemptions" : { "type" : "integer" }
    }
  }
}'
echo

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/datacube_loyaltyprogramschanges -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["t*_datacube_loyaltyprogramschanges"],
  "mappings" : {
    "_meta": { "datacube_loyaltyprogramschanges" : { "version": Deployment.getElasticsearchDatacubeLoyaltyprogramschangesTemplateVersion() } },
    "properties" : {
      "timestamp" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" },
      "period" : { "type" : "long" },
      "filter.tenantID" : { "type" : "integer" },
      "filter.loyaltyProgram" : { "type" : "keyword" },
      "filter.newTier" : { "type" : "keyword" },
      "filter.previousTier" : { "type" : "keyword" },
      "filter.tierChangeType" : { "type" : "keyword" },
      "count" : { "type" : "integer" }
    }
  }
}'
echo

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/datacube_challengeshistory -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["t*_datacube_challengeshistory"],
  "mappings" : {
    "_meta": { "datacube_challengeshistory" : { "version": Deployment.getElasticsearchDatacubeChallengeshistoryTemplateVersion() } },
    "properties" : {
      "timestamp" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" },
      "period" : { "type" : "long" },
      "filter.tenantID" : { "type" : "integer" },
      "filter.loyaltyProgram" : { "type" : "keyword" },
      "filter.level" : { "type" : "keyword" },
      "filter.occurrenceNumber" : { "type" : "integer" },
      "count" : { "type" : "integer" },
      "metric.score" : { "type" : "integer" }
    }
  }
}'
echo

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/datacube_challengeschanges -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["t*_datacube_challengeschanges"],
  "mappings" : {
    "_meta": { "datacube_challengeschanges" : { "version": Deployment.getElasticsearchDatacubeChallengeschangesTemplateVersion() } },
    "properties" : {
      "timestamp" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" },
      "period" : { "type" : "long" },
      "filter.tenantID" : { "type" : "integer" },
      "filter.loyaltyProgram" : { "type" : "keyword" },
      "filter.newLevel" : { "type" : "keyword" },
      "filter.previousLevel" : { "type" : "keyword" },
      "filter.levelChangeType" : { "type" : "keyword" },
      "filter.occurrenceNumber" : { "type" : "integer" },
      "count" : { "type" : "integer" }
    }
  }
}'
echo

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/datacube_missionshistory -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["t*_datacube_missionshistory"],
  "mappings" : {
    "_meta": { "datacube_datacube_missionshistory" : { "version": Deployment.getElasticsearchDatacubeMissionshistoryTemplateVersion() } },
    "properties" : {
      "timestamp" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" },
      "period" : { "type" : "long" },
      "filter.tenantID" : { "type" : "integer" },
      "filter.loyaltyProgram" : { "type" : "keyword" },
      "filter.step" : { "type" : "keyword" },
      "filter.occurrenceNumber" : { "type" : "integer" },
      "count" : { "type" : "integer" },
      "metric.progression" : { "type" : "long" }
    }
  }
}'
echo

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/datacube_missionschanges -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["t*_datacube_missionschanges"],
  "mappings" : {
    "_meta": { "datacube_missionschanges" : { "version": Deployment.getElasticsearchDatacubeMissionschangesTemplateVersion() } },
    "properties" : {
      "timestamp" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" },
      "period" : { "type" : "long" },
      "filter.tenantID" : { "type" : "integer" },
      "filter.loyaltyProgram" : { "type" : "keyword" },
      "filter.newStep" : { "type" : "keyword" },
      "filter.previousStep" : { "type" : "keyword" },
      "filter.stepChangeType" : { "type" : "keyword" },
      "count" : { "type" : "integer" }
    }
  }
}'
echo

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/datacube_journeytraffic- -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["t*_datacube_journeytraffic-*"],
  "mappings" : {
    "_meta": { "datacube_journeytraffic" : { "version": Deployment.getElasticsearchDatacubeJourneytrafficTemplateVersion() } },
    "properties" : {
      "timestamp" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" },
      "period" : { "type" : "long" },
      "filter.tenantID" : { "type" : "integer" },
      "filter.journey" : { "type" : "keyword" },
      "filter.node" : { "type" : "keyword" },
      "filter.status" : { "type" : "keyword" },
      "count" : { "type" : "integer" },
      "metric.conversions" : { "type" : "integer" },
      "metric.converted.today" : { "type" : "integer" }
    }
  }
}'
echo

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/datacube_journeyrewards- -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["t*_datacube_journeyrewards-*"],
  "mappings" : {
    "_meta": { "datacube_journeyrewards" : { "version": Deployment.getElasticsearchDatacubeJourneyrewardsTemplateVersion() } },
    "properties" : {
      "timestamp" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" },
      "period" : { "type" : "long" },
      "filter.tenantID" : { "type" : "integer" },
      "filter.journey" : { "type" : "keyword" },
      "count" : { "type" : "integer" }
    }
  }
}'
echo

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/datacube_odr -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["t*_datacube_odr"],
  "mappings" : {
    "_meta": { "datacube_odr" : { "version": Deployment.getElasticsearchDatacubeOdrTemplateVersion() } },
    "properties" : {
      "timestamp" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" },
      "period" : { "type" : "long" },
      "filter.tenantID" : { "type" : "integer" },
      "filter.offer" : { "type" : "keyword" },
      "filter.module" : { "type" : "keyword" },
      "filter.feature" : { "type" : "keyword" },
      "filter.salesChannel" : { "type" : "keyword" },
      "filter.meanOfPayment" : { "type" : "keyword" },
      "filter.meanOfPaymentProviderID" : { "type" : "keyword" },
      "filter.offerObjectives" : { "type" : "keyword" },
      "filter.origin" : { "type" : "keyword" },
      "count" : { "type" : "integer" },
      "metric.totalAmount" : { "type" : "integer" }
    }
  }
}'
echo

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/datacube_bdr -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["t*_datacube_bdr"],
  "mappings" : {
    "_meta": { "datacube_bdr" : { "version": Deployment.getElasticsearchDatacubeBdrTemplateVersion() } },
    "properties" : {
      "timestamp" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" },
      "period" : { "type" : "long" },
      "filter.tenantID" : { "type" : "integer" },
      "filter.module" : { "type" : "keyword" },
      "filter.feature" : { "type" : "keyword" },
      "filter.provider" : { "type" : "keyword" },
      "filter.operation" : { "type" : "keyword" },
      "filter.salesChannel" : { "type" : "keyword" },
      "filter.deliverable" : { "type" : "keyword" },
      "filter.returnCode" : { "type" : "keyword" },
      "filter.origin" : { "type" : "keyword" },
      "count" : { "type" : "integer" },
      "metric.totalQty" : { "type" : "integer" }
    }
  }
}'
echo

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/datacube_tdr -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["t*_datacube_tdr"],
  "mappings" : {
    "_meta": { "datacube_tdr" : { "version": Deployment.getElasticsearchDatacubeTdrTemplateVersion() } },
    "properties" : {
      "period" : { "type" : "long" },
      "filter.module" : { "type" : "keyword" },
      "filter.feature" : { "type" : "keyword" },      
      "filter.origin" : { "type" : "keyword" },
      "filter.returnCode" : { "type" : "keyword" },
      "filter.acceptedOffer" : { "type" : "keyword" },
      "filter.action" : { "type" : "keyword" },
      "count" : { "type" : "integer" },
      "timestamp" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" }
    }
  }
}'
echo

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/datacube_messages -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["t*_datacube_messages"],
  "mappings" : {
    "_meta": { "datacube_messages" : { "version": Deployment.getElasticsearchDatacubeMessagesTemplateVersion() } },
    "properties" : {
      "timestamp" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" },
      "period" : { "type" : "long" },
      "filter.tenantID" : { "type" : "integer" },
      "filter.module" : { "type" : "keyword" },
      "filter.feature" : { "type" : "keyword" },
      "filter.provider" : { "type" : "keyword" },
      "filter.language" : { "type" : "keyword" },
      "filter.template" : { "type" : "keyword" },
      "filter.channel" : { "type" : "keyword" },
      "filter.contactType" : { "type" : "keyword" },
      "filter.returnCode" : { "type" : "keyword" },
      "count" : { "type" : "integer" },
      "metric.totalQty" : { "type" : "integer" },
      "filter.origin" : { "type" : "keyword" }
    }
  }
}'
echo

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/datacube_vdr -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["t*_datacube_vdr"],
  "mappings" : {
    "_meta": { "datacube_vdr" : { "version": Deployment.getElasticsearchDatacubeVdrTemplateVersion() } },
    "properties" : {
      "timestamp" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" },
      "period" : { "type" : "long" },
      "filter.voucher" : { "type" : "keyword" },
      "filter.supplier" : { "type" : "keyword" },
      "filter.module" : { "type" : "keyword" },
      "filter.feature" : { "type" : "keyword" },      
      "filter.action" : { "type" : "keyword" },
      "filter.origin" : { "type" : "keyword" },
      "filter.returnCode" : { "type" : "keyword" },
      "count" : { "type" : "integer" },
      "metric.totalAmount" : { "type" : "integer" }
    }
  }
}'
echo
# -------------------------------------------------------------------------------
#
# mappings
#
# -------------------------------------------------------------------------------
#
# mapping_modules is a static index, filled at deployment.
# Rows are manually insert below.
#
prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/mapping_modules -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["mapping_modules*"],
  "mappings" : {
    "_meta": { "mapping_modules" : { "version": Deployment.getElasticsearchMappingModulesTemplateVersion() } },
    "properties" : {
      "moduleID" : { "type" : "keyword" },
      "moduleName" : { "type" : "keyword" },
      "moduleDisplay" : { "type" : "keyword" },
      "moduleFeature" : { "type" : "keyword" }
    }
  }
}'
echo

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/mapping_modules/_doc/1 -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "moduleID" : "1", "moduleName": "Journey_Manager", "moduleDisplay" : "Journey Manager", "moduleFeature" : "journeyID"
}'
echo

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/mapping_modules/_doc/2 -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "moduleID" : "2", "moduleName": "Loyalty_Program", "moduleDisplay" : "Loyalty Program", "moduleFeature" : "loyaltyProgramID"
}'
echo

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/mapping_modules/_doc/3 -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "moduleID" : "3", "moduleName": "Offer_Catalog", "moduleDisplay" : "Offer Catalog", "moduleFeature" : "offerID"
}'
echo

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/mapping_modules/_doc/4 -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "moduleID" : "4", "moduleName": "Delivery_Manager", "moduleDisplay" : "Delivery Manager", "moduleFeature" : "deliverableID"
}'
echo

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/mapping_modules/_doc/5 -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "moduleID" : "5", "moduleName": "Customer_Care", "moduleDisplay" : "Customer Care", "moduleFeature" : "none"
}'
echo

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/mapping_modules/_doc/6 -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "moduleID" : "6", "moduleName": "REST_API", "moduleDisplay" : "REST API", "moduleFeature" : "none"
}'
echo

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/mapping_modules/_doc/999 -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "moduleID" : "999", "moduleName": "Unknown", "moduleDisplay" : "Unknown", "moduleFeature" : "none"
}'
echo

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/mapping_journeys -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["mapping_journeys*"],
  "mappings" : {
    "_meta": { "mapping_journeys" : { "version": Deployment.getElasticsearchMappingJourneysTemplateVersion() } },
    "properties" : {
      "journeyID" : { "type" : "keyword" },
      "tenantID" : { "type" : "integer" },
      "display" : { "type" : "keyword" },
      "description" : { "type" : "keyword" },
      "type" : { "type" : "keyword" },
      "user" : { "type" : "keyword" },
      "targets" : { "type" : "keyword" },
      "targetCount" : { "type" : "integer" },
      "objectives" : { "type" : "keyword" },
      "startDate" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" },
      "endDate" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" },
      "active" : { "type" : "boolean" },
      "timestamp" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" }
    }
  }
}'
echo

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/mapping_journeyrewards -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["mapping_journeyrewards*"],
  "mappings" : {
    "_meta": { "mapping_journeyrewards" : { "version": Deployment.getElasticsearchMappingJourneyrewardsTemplateVersion() } },
    "properties" : {
      "journeyID" : { "type" : "keyword" },
      "reward" : { "type" : "keyword" },
      "timestamp" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" }
    }
  }
}'
echo

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/mapping_deliverables -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["mapping_deliverables*"],
  "mappings" : {
    "_meta": { "mapping_deliverables" : { "version": Deployment.getElasticsearchMappingDeliverablesTemplateVersion() } },
    "properties" : {
      "deliverableID" : { "type" : "keyword" },
      "deliverableName" : { "type" : "keyword" },
      "deliverableActive" : { "type" : "boolean" },
      "deliverableProviderID" : { "type" : "keyword" }
    }
  }
}'
echo

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/mapping_partners -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["mapping_partners*"],
  "mappings" : {
  "_meta": { "mapping_partners" : { "version": Deployment.getElasticsearchMappingPartnersTemplateVersion() } },
    "properties" : {
      "createdDate" : 	{ "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" },
      "display" : 		{ "type" : "keyword" },
      "active" : 		{ "type" : "boolean" },
      "partnerType" : 	{ "type" : "keyword" },
	  "id" : 			{ "type" : "keyword" },
	  "email" : 		{ "type" : "keyword" },
	  "parentId" : 		{ "type" : "keyword" },
	  "provider" : 		{ "type" : "keyword" },
	  "address" : 		{ "type" : "keyword" },
	  "timestamp" : 	{ "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" }
    }
  }
}'
echo

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/mapping_badges -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["mapping_badges*"],
  "mappings" : {
  "_meta": { "mapping_badges" : { "version": Deployment.getElasticsearchMappingBadgesTemplateVersion() } },
    "properties" : {
	  "id" : 			{ "type" : "keyword" },
	  "display" : 		{ "type" : "keyword" },
      "active" : 		{ "type" : "boolean" },
      "badgeType" : 	{ "type" : "keyword" },
      "tenantID"  : 	{ "type" : "integer" },
      "createdDate" : 	{ "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" },
	  "timestamp" : 	{ "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" }
    }
  }
}'
echo

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/mapping_basemanagement -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["mapping_basemanagement*"],
  "mappings" : {
    "_meta": { "mapping_basemanagement" : { "version": Deployment.getElasticsearchMappingBasemanagementTemplateVersion() } },
    "properties" : {
      "id"            : { "type" : "keyword" },
      "display"       : { "type" : "keyword" },
      "active"        : { "type" : "boolean" },
      "targetingType" : { "type" : "keyword" },
      "segments"      : { "type" : "nested",
      	   "properties" : {
              "id"    : { "type" : "keyword" },
              "name"  : { "type" : "keyword" }
           }
      },
      "createdDate"   : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" },
      "timestamp"     : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" }
    }
  }
}'
echo

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/mapping_journeyobjective -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["mapping_journeyobjective*"],
  "mappings" : {
    "_meta": { "mapping_journeyobjective" : { "version": Deployment.getElasticsearchMappingJourneyobjectiveTemplateVersion() } },
    "properties" : {
      "id"            : { "type" : "keyword" },
      "display"       : { "type" : "keyword" },
      "contactPolicy" : { "type" : "keyword" },
      "timestamp"     : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ" }
    }
  }
}'
echo

#
#  manually create edr template
#

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/edr -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["detailedrecords_events-*"],
  "settings" : {
    "opendistro.index_state_management.policy_id": "edr_policy"
  },
  "mappings" : {
    "_meta": { "edr" : { "version": Deployment.getElasticsearchEdrTemplateVersion() } },
    "dynamic_date_formats": ["yyyy-MM-dd HH:mm:ss.SSSZ"],
    "properties" : {
      "subscriberID" : { "type" : "keyword" }
    }
  }
}'
echo

# -------------------------------------------------------------------------------
#
# maintenance
#
# -------------------------------------------------------------------------------

# maintenance_action_request is a static index, filled by maintenance job guimanager - a sample value populated/deleted with complete req to crete the index.
prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/maintenance_action_request -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["maintenance_action_request*"],
  "mappings" : {
    "_meta": { "maintenance_action_request" : { "version": Deployment.getElasticsearchDefaultShards() } },
	"properties": {
      "requestedBy" : { "type" : "keyword" },
      "requestDate" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ"},
      "status" : { "type" : "keyword" }
    }
  }
}'
echo

#
# sample value - create
#

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/maintenance_action_request/_doc/-1 -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "requestedBy" : "dummy-deployment", "status" : "COMPLETED"
}'
echo

# maintenance_action_log is a static index, filled by maintenance job script - a sample value populated/deleted to crete the index.
prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/_template/maintenance_action_log -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "index_patterns": ["maintenance_action_log*"],
  "mappings" : {
    "_meta": { "maintenance_action_log" : { "version": Deployment.getElasticsearchDefaultShards() } },
	"properties": {
      "actionType" : { "type" : "keyword" },
      "requestId" : { "type" : "keyword" },
      "node" : { "type" : "keyword" },
      "user" : { "type" : "keyword" },
      "actionStartDate" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ"},
      "actionEndDate" : { "type" : "date", "format":"yyyy-MM-dd HH:mm:ss.SSSZZ"},
      "actionLog" : { "type" : "keyword" },
      "status" : { "type" : "keyword" },
      "remarks" : { "type" : "keyword" }
    }
  }
}'
echo

#
# sample value - create
#

prepare-es-update-curl -XPUT https://$MASTER_ESROUTER_SERVER/maintenance_action_log/_doc/-1 -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
  "requestId" : "-1", "node" : "dummy", "user" : "deployment"
}'
echo

#
# sample value - delete
#

prepare-es-update-curl -XDELETE https://$MASTER_ESROUTER_SERVER/maintenance_action_log/_doc/-1 -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
}'
echo


#
# sample value - delete
#

prepare-es-update-curl -XDELETE https://$MASTER_ESROUTER_SERVER/maintenance_action_request/_doc/-1 -u $ELASTICSEARCH_USERNAME:$ELASTICSEARCH_USERPASSWORD -H'Content-Type: application/json' -d'
{
}'
echo
