'use strict';

/* Controllers */
var mainController = angular.module('mainController', []);

mainController.controller('MainController', function MainController( $scope, SlotService) {
     SlotService.get(function (slots) {
            console.log("Received slots "+slots);
            $scope.slots = slots;
        });
});
