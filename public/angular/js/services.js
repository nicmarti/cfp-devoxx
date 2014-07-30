'use strict';

/* Services */
//
var remoteServices = angular.module('remoteServices', ['ngResource']);

remoteServices.factory('SlotService', ['$resource', function($resource) {
   return $resource('/schedulling/slots', null, {'query': {method: 'GET', isArray: true, responseType:'json'}});
}]);

remoteServices.factory('ApprovedTalksService', ['$resource', function($resource) {
   return $resource('/schedulling/approvedTalks', null, {'query': {method: 'GET', isArray: true, responseType:'json'}});
}]);

remoteServices.factory('SaveSlotService', ['$resource', function($resource) {
   return $resource('/schedulling/slots', null, {'save': { method:'POST'}});
}]);

remoteServices.factory('AllScheduledConfiguration', ['$resource', function($resource){
    return $resource('/schedulling/scheduledConfigurations', null, {'query': {method: 'GET', isArray: true, responseType:'json'}})
}]);

remoteServices.factory('ReloadScheduleConf', ['$resource', function($resource){
    return $resource('/schedulling/loadScheduledConfiguration', null, {'query': {method: 'GET', isArray: false, responseType:'json'}})
}]);

remoteServices.factory('DeleteScheduledConfiguration', ['$resource', function($resource){
  return $resource('/schedulling/deletescheduledConfigurations', null, {'query': {method: 'DELETE', isArray: false, responseType:'json'}})
}]);

remoteServices.factory('PublishScheduledConfiguration', ['$resource', function($resource){
  return $resource('/schedulling/publish', null, {'save': { method:'POST'}});
}]);