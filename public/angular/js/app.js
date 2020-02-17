'use strict';


// Declare app level module which depends on filters, and services
var cfpApp = angular.module('cfpApp', [
    'ngRoute',
    'ngResource',
    'remoteServices',
    'mainController',
    'homeController',
    'programController',
    'reloadScheduleConfController',
    'deleteSlotController',
    'publishController',
    'flash'
]);

cfpApp.config(['$routeProvider', function ($routeProvider) {
    $routeProvider.when('/slots', {
        templateUrl: 'partials/slots.html',
        controller: 'MainController'
    });
    $routeProvider.when('/program', {
        templateUrl: 'partials/program.html',
        controller: 'ProgramController'
    });
    $routeProvider.when('/saved', {
        templateUrl: 'partials/savedSlots.html',
        controller: 'HomeController'
    });
    $routeProvider.when('/slots/:id', {
        templateUrl: 'partials/loadSlot.html',
        controller: 'ReloadScheduleConfController'
    });

    $routeProvider.otherwise({redirectTo: '/'});
}]);

