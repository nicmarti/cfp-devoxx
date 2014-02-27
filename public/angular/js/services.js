'use strict';

/* Services */
//
var remoteServices = angular.module('remoteServices', ['ngResource']);

remoteServices.factory('SlotService', ['$resource', function($resource) {
   return $resource('/api/fr14/slots', null, {'query': {method: 'GET', isArray: true, responseType:'json'}});
}]);

remoteServices.factory('ApprovedTalksService', ['$resource', function($resource) {
   return $resource('/api/fr14/approvedTalks', null, {'query': {method: 'GET', isArray: true, responseType:'json'}});
}]);

remoteServices.factory('SaveSlotService', ['$resource', function($resource) {
   return $resource('/api/fr14/slots', null, {'save': { method:'POST'}});
}]);

remoteServices.factory('AllScheduledConfiguration', ['$resource', function($resource){
    return $resource('/api/fr14/scheduledConfigurations', null, {'query': {method: 'GET', isArray: true, responseType:'json'}})
}]);

remoteServices.factory('ScheduledConfiguration', ['$resource', function($resource){
    return $resource('/api/fr14/loadScheduledConfiguration', null, {'query': {method: 'GET', isArray: false, responseType:'json'}})
}]);
