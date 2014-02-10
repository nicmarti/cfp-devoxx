'use strict';

/* Controllers */
var mainController = angular.module('mainController', []);

mainController.controller('MainController', function MainController($rootScope, $scope, $routeParams, SlotService, AcceptedTalksService) {
    SlotService.get(function (allSlots) {
        $scope.slots = allSlots["slots"];
    });

    AcceptedTalksService.get({confType: $routeParams.confType}, function (allAccepted) {
        $scope.acceptedTalks = allAccepted["acceptedTalks"];
    });

    $rootScope.$on('dropEvent', function (evt, dragged, dropped) {
        console.log(dragged);
        console.log(dropped);

        dropped.proposalId = dragged.id;
        dropped.proposalTitle = dragged.title;
        dropped.proposalLang = dragged.lang;
        dropped.proposalTrack = dragged.track.id;
        dropped.proposalSpeaker = dragged.mainSpeaker;

        var maybeSlot = _.find($scope.slots, function (slot) {
            return slot.id == dropped.id;
        });
        if (_.isUndefined(maybeSlot)) {
            console.log("old slot not found");
        } else {
            // Update the slot
            maybeSlot.proposalId = dragged.id;
            maybeSlot.proposalTitle = dragged.title;
            maybeSlot.proposalLang = dragged.lang;

            // remove from accepted talks
            $scope.acceptedTalks.talks = _.reject($scope.acceptedTalks.talks, function (a) {
                return a.id === dragged.id
            });

            $scope.$apply();


        }
    });
});
