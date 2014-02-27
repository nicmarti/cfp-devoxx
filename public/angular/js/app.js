'use strict';


// Declare app level module which depends on filters, and services
var cfpApp = angular.module('cfpApp', [
    'ngRoute',
    'ngResource',
    'remoteServices',
    'mainController',
    'homeController',
    'scheduleConfController'
]);

cfpApp.config(['$routeProvider', function ($routeProvider) {
    $routeProvider.when('/saved', {
        templateUrl: 'partials/savedSlots.html',
        controller: 'HomeController'
    });
    $routeProvider.when('/slot/:id', {
        templateUrl: 'partials/loadSlot.html',
        controller: 'ScheduleConfController'
    });
    $routeProvider.when('/slots', {
        templateUrl: 'partials/slots.html',
        controller: 'MainController'
    });

    $routeProvider.otherwise({redirectTo: '/'});
}]);
