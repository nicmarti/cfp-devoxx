'use strict';


// Declare app level module which depends on filters, and services
var cfpApp = angular.module('cfpApp', [
  'ngRoute',
  'ngResource',
  'remoteServices',
  'mainController'
]);

cfpApp.config(['$routeProvider', function($routeProvider) {
  $routeProvider.when('/slots', {
      templateUrl: 'partials/slots.html',
      controller: 'MainController'
  });
  $routeProvider.otherwise({redirectTo: '/'});
}]);


