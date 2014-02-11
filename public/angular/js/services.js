'use strict';

/* Services */
//
var remoteServices = angular.module('remoteServices', ['ngResource']);

remoteServices.factory('SlotService', ['$resource', function($resource) {
   return $resource('/api/fr14/slots/:confType', {params:{confType:'uni'}}, {'query': {method: 'GET', isArray: true, responseType:'json'}});
}]);

remoteServices.factory('ApprovedTalksService', ['$resource', function($resource) {
   return $resource('/api/fr14/approvedTalks/:confType', {params:{confType:'uni'}}, {'query': {method: 'GET', isArray: true, responseType:'json'}});
}]);

remoteServices.factory('SaveSlotService', ['$resource', function($resource) {
   return $resource('/api/fr14/slots/:confType', null, {'save': { method:'POST'}});
}]);
