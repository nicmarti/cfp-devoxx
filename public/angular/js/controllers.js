'use strict';

/* Controllers */
var mainController = angular.module('mainController', []);

mainController.controller('MainController', function MainController($rootScope, $scope, $routeParams, SlotService, AcceptedTalksService) {
    SlotService.get(function (jsonArray) {
        $scope.slots = jsonArray["allSlots"];
    });

    AcceptedTalksService.get({confType: $routeParams.confType}, function (allAccepted) {
        $scope.acceptedTalks = allAccepted["acceptedTalks"];
    });

    $rootScope.$on('dropEvent', function (evt, dragged, dropped) {

        var maybeSlot2 = _.find($scope.slots, function (slot) {
            return slot.id == dropped.id;
        });
        if (_.isUndefined(maybeSlot2)) {
            console.log("old slot not found");
        } else {
            if(_.isUndefined(maybeSlot2.proposal)==false){
                // if there is a talk, remove it
                var oldTalk=maybeSlot2.proposal ;

                // Remove from left
                 maybeSlot2.proposal=undefined;
            }

            // Update the slot
            maybeSlot2.proposal = dragged;

            // remove from accepted talks
            $scope.acceptedTalks.talks = _.reject($scope.acceptedTalks.talks, function (a) {
                return a.id === dragged.id
            });
            // Add back to right
            if(_.isUndefined(oldTalk)==false){
                $scope.acceptedTalks.talks = $scope.acceptedTalks.talks.concat(oldTalk);
            }

            $scope.$apply();
        }
    });

    $scope.unallocate = function(slotId){
       var maybeSlot = _.find($scope.slots, function (slot) {
            return slot.id == slotId;
        });
        if (_.isUndefined(maybeSlot)) {
            console.log("old slot not found");
        } else {
            var talk=maybeSlot.proposal ;

            // Remove from left
            maybeSlot.proposal=undefined;

            // Add back to right
            $scope.acceptedTalks.talks = $scope.acceptedTalks.talks.concat(talk);
        }
    };

});
