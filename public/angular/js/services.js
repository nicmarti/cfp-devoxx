'use strict';

/* Services */
//
var remoteServices = angular.module('remoteServices', ['ngResource']);

remoteServices.factory('SlotService', ['$resource', function($resource) {
   return $resource('/api/fr14/slots', {}, {'query': {method: 'GET', isArray: true, responseType:'json'}});
}]);