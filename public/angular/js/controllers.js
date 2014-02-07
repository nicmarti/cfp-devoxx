'use strict';

/* Controllers */
var mainController = angular.module('mainController', []);

mainController.controller('MainController', function MainController( $scope, SlotService) {
     SlotService.get(function (allSlots) {
            $scope.slots = allSlots["slots"];
        });
});
