'use strict';

/* Controllers */
var mainController = angular.module('mainController', []);
var homeController = angular.module('homeController', []);
var scheduleConfController = angular.module('scheduleConfController', []);
var loadSlotController = angular.module('loadSlotController', []);

mainController.controller('MainController', function MainController($rootScope, $scope, $routeParams, SlotService, ApprovedTalksService) {
    SlotService.get({confType: $routeParams.confType}, function (jsonArray) {
        $scope.slots = jsonArray["allSlots"];
    });

    $scope.showLang = 'all';
    $scope.showCurrentTrack = undefined;
    $scope.listOfTracks=[];

    ApprovedTalksService.get({confType: $routeParams.confType}, function (allApproved) {
        $rootScope.allApprovedTalks = allApproved["approvedTalks"];
        $scope.approvedTalks = $rootScope.allApprovedTalks.talks;


       var listOfTracks = _.map($rootScope.allApprovedTalks.talks,function(talk){
            return talk.track.id;
        });

        $scope.listOfTracks = _.uniq(listOfTracks);
        $scope.listOfTracks.push("All");
        $scope.showCurrentTrack = "All";
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
            $scope.approvedTalks = _.reject($scope.approvedTalks, function (a) {
                return a.id === dragged.id
            });
            // Add back to right
            if(_.isUndefined(oldTalk)==false){
                $scope.approvedTalks = $scope.approvedTalks.concat(oldTalk);
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
            $scope.approvedTalks = $scope.approvedTalks.concat(talk);
        }
    };

    $scope.saveAllocation=function(){
        SlotService.save({confType: $routeParams.confType}, $scope.slots);
    };

    $scope.$watch('showLang', function(newValue, oldValue) {
        // Ignore initial setup.
        if (newValue === oldValue) {
            return;
        }

        if(newValue=="all"){
            $scope.approvedTalks = $rootScope.allApprovedTalks.talks;
            $scope.showCurrentTrack="All";
            return;
        }
        var filteredArrayOfTalks =_.filter($rootScope.allApprovedTalks.talks , function(talk){
                if($scope.showCurrentTrack!="All"){
                    return (talk.lang == $scope.showLang && talk.track.id == $scope.showCurrentTrack);
                }else{
                     return talk.lang == $scope.showLang;
                }
        });
        $scope.approvedTalks=filteredArrayOfTalks;
        //$scope.$apply();
    });

      $scope.$watch('showCurrentTrack', function(newValue, oldValue) {
        // Ignore initial setup.
        if (newValue === oldValue) {
            return;
        }

        if(newValue=="All"){
            $scope.approvedTalks = $rootScope.allApprovedTalks.talks;
            $scope.showLang="all";
            return;
        }

        var filteredArrayOfTalks =_.filter($rootScope.allApprovedTalks.talks , function(talk){
            if($scope.showLang!="all"){
                return (talk.lang == $scope.showLang && talk.track.id == $scope.showCurrentTrack);
            }else{
                return talk.track.id == $scope.showCurrentTrack;
            }
        });

        $scope.approvedTalks=filteredArrayOfTalks;
        //$scope.$apply();
    });
});

homeController.controller('HomeController', function HomeController($rootScope, $scope, $routeParams, AllScheduledConfiguration) {
    AllScheduledConfiguration.get(function(jsonArray){
       $scope.allScheduledConfiguration = jsonArray["scheduledConfigurations"];
    });
});

scheduleConfController.controller('ScheduleConfController', function ScheduleConfController($location, $rootScope, $scope, $routeParams, ScheduledConfiguration) {
    ScheduledConfiguration.get({id: $routeParams.id}, function (jsonObj){
       $scope.loadedScheduledConfiguration = jsonObj;
        if (_.isUndefined($scope.loadedScheduledConfiguration)) {
            console.log("ERR: conf type not found");
        } else {
            var newConfType = $scope.loadedScheduledConfiguration.confType;
            $rootScope.slots = $scope.loadedScheduledConfiguration.slots;
            $location.path('/loadSlots').search({confType: newConfType}).replace();
        }
    });


});

loadSlotController.controller('LoadSlotController', function LoadSlotController($rootScope, $scope, $routeParams, SlotService ) {

    $scope.slots = $rootScope.slots;

    $scope.approvedTalks = [];
    $scope.approvedTalks.talks = [];


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
            $scope.approvedTalks.talks = _.reject($scope.approvedTalks.talks, function (a) {
                return a.id === dragged.id
            });
            // Add back to right
            if(_.isUndefined(oldTalk)==false){
                $scope.approvedTalks.talks = $scope.approvedTalks.talks.concat(oldTalk);
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
            $scope.approvedTalks.talks = $scope.approvedTalks.talks.concat(talk);
        }
    };

    $scope.saveAllocation=function(){
        SlotService.save({confType: $routeParams.confType}, $scope.slots);
    };

});