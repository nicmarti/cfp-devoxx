'use strict';

/* Controllers */
var mainController = angular.module('mainController', []);

mainController.controller('MainController', function MainController($rootScope, $scope, $routeParams, SlotService, AcceptedTalksService) {
    SlotService.get(function (allSlots) {
        $scope.slots = allSlots["slots"];
    });

    AcceptedTalksService.get({confType: $routeParams.confType},function(allAccepted){
        $scope.acceptedTalks = allAccepted["acceptedTalks"];
    });

    $rootScope.$on('dropEvent', function(evt, dragged, dropped) {
        console.log(dragged);
        console.log(dropped);
//        var i, oldIndex1, oldIndex2;
//        for(i=0; i<$scope.columns.length; i++) {
//            var c = $scope.columns[i];
//            if(dragged.title === c.title) {
//                oldIndex1 = i;
//            }
//            if(dropped.title === c.title) {
//                oldIndex2 = i;
//            }
//        }
//        var temp = $scope.columns[oldIndex1];
//        $scope.columns[oldIndex1] = $scope.columns[oldIndex2];
//        $scope.columns[oldIndex2] = temp;
//        $scope.$apply();
    });
});
