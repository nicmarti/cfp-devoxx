'use strict';

/* Services */
//
var remoteServices = angular.module('remoteServices', ['ngResource']);

remoteServices.factory('SlotService', ['$resource', function($resource) {
   return $resource('/schedulling/fr14/slots', null, {'query': {method: 'GET', isArray: true, responseType:'json'}});
}]);

remoteServices.factory('ApprovedTalksService', ['$resource', function($resource) {
   return $resource('/schedulling/fr14/approvedTalks', null, {'query': {method: 'GET', isArray: true, responseType:'json'}});
}]);

remoteServices.factory('SaveSlotService', ['$resource', function($resource) {
   return $resource('/schedulling/fr14/slots', null, {'save': { method:'POST'}});
}]);

remoteServices.factory('AllScheduledConfiguration', ['$resource', function($resource){
    return $resource('/schedulling/fr14/scheduledConfigurations', null, {'query': {method: 'GET', isArray: true, responseType:'json'}})
}]);

remoteServices.factory('ReloadScheduleConf', ['$resource', function($resource){
    return $resource('/schedulling/fr14/loadScheduledConfiguration', null, {'query': {method: 'GET', isArray: false, responseType:'json'}})
}]);

remoteServices.factory('DeleteScheduledConfiguration', ['$resource', function($resource){
  return $resource('/schedulling/fr14/deletescheduledConfigurations', null, {'query': {method: 'DELETE', isArray: false, responseType:'json'}})
}]);

remoteServices.factory('PublishScheduledConfiguration', ['$resource', function($resource){
  return $resource('/schedulling/fr14/publish', null, {'save': { method:'POST'}});
}]);