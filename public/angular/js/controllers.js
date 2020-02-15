'use strict';

/* Controllers */
var mainController = angular.module('mainController', []);
var homeController = angular.module('homeController', []);
var programController = angular.module('programController', []);
var reloadScheduleConfController = angular.module('reloadScheduleConfController', []);
var deleteSlotController = angular.module('deleteSlotController', []);
var publishController = angular.module('publishController', []);


homeController.controller('HomeController', function HomeController($rootScope, $scope, $routeParams, AllScheduledConfiguration) {
    AllScheduledConfiguration.get(function(jsonArray){
       $scope.allScheduledConfiguration = jsonArray["scheduledConfigurations"];
    });
});

programController.controller('ProgramController', function ProgramController($rootScope, $scope, $routeParams, flash, ProgramScheduleResource, CreateAndPublishEmptyProgramSchedule) {
    $scope.refresh = function() {
        ProgramScheduleResource.get(function(result) {
            $scope.programSchedules = result.programSchedules;
            $scope.savedConfigurations = result.savedConfigurations;
            $scope.savedConfigurations.sort((c1, c2) => c2.latestModification - c1.latestModification);
            $scope.slottableProposalTypes = result.slottableProposalTypes;
        });
    };

    $scope.atLeastOneReadonlyProgramSchedule = function() {
        if(!$scope.programSchedules) {
            return false;
        }

        return !!$scope.programSchedules.filter(s => !s.isEditable).length;
    };

    $scope.atLeastOnePublishedProgramSchedule = function() {
        if(!$scope.programSchedules) {
            return false;
        }

        return !!$scope.programSchedules.filter(s => s.isTheOnePublished).length;
    };

    $scope.createAndPublishEmptyProgramSchedule = function() {
        $scope.createAndPublishEmptyProgramScheduleInProgress = true;
        CreateAndPublishEmptyProgramSchedule.save(function () {
            $scope.createAndPublishEmptyProgramScheduleInProgress = false;
            $scope.refresh();
        });
    };

    $scope.scheduleConfigById = function(scheduleConfigId) {
        return $scope.savedConfigurations.find(config => config.id === scheduleConfigId);
    };

    $scope.availableConfigsForType = function(confType) {
        return $scope.savedConfigurations.filter(config => config.confType === confType);
    };

    $scope.createNewProgramSchedule = function() {
        $scope.programSchedules.unshift({
            id: "",
            name: "",
            lastModifiedByName: "Me",
            lastModified: Date.now(),
            isEditable: true,
            isTheOnePublished: false,
            scheduleConfigurations: {},
            _isEdited: true
        });
    };

    $scope.saveProgramSchedule = function(programSchedule) {
        programSchedule._saveProgramScheduleInProgress = true;
        var operation = programSchedule.id?"update":"save";
        ProgramScheduleResource[operation]({ ...programSchedule, eventCode: "" }, function(persistedProgramSchedule){
            programSchedule._saveProgramScheduleInProgress = false;
            $scope.refresh();
        });
    };

    $scope.deleteProgramSchedule = function(programSchedule) {
      if(confirm("Do you really want to delete this Program schedule ?")) {
        programSchedule._deleteProgramScheduleInProgress = true;
        ProgramScheduleResource.delete({ id: programSchedule.id }, function() {
          programSchedule._deleteProgramScheduleInProgress = true;
          $scope.refresh();
        });
      }
    };

    $scope.publishProgramSchedule = function(programSchedule) {
        programSchedule._publishProgramScheduleInProgress = true;
        ProgramScheduleResource.publish({id: programSchedule.id}, function(){
            programSchedule._publishProgramScheduleInProgress = true;
            $scope.refresh();
        });
    };

    $scope.refresh();
});

mainController.controller('MainController', function MainController($rootScope, $scope, $routeParams, SlotService, ApprovedTalksService, flash) {

    // Left column, list of accepted proposal
    ApprovedTalksService.get({confType: $routeParams.confType}, function (allApproved) {
        // If a ScheduleConfiguration was reloaded, then we need to filter-out the list of ApprovedTalks
        if (_.isUndefined($rootScope.slots) == false) {
            var onlyValidProposals = _.reject($rootScope.slots, function(slot){ return _.isUndefined(slot.proposal)} );
            var onlyIDs= _.map(onlyValidProposals, function(slot){return slot.proposal.id; });

            $scope.approvedTalks=_.reject(allApproved["approvedTalks"].talks, function (talk) {
                return _.contains(onlyIDs, talk.id);
            });
        } else {
            console.log("No schedule configuration loaded");
            $scope.approvedTalks =  allApproved["approvedTalks"].talks ;
            $rootScope.title = "Create a new " + $routeParams.confType + " schedule from scratch"
        }
    });

    // Load a schedule configuration
    SlotService.get({confType: $routeParams.confType}, function (jsonArray) {
        $scope.slots = jsonArray["allSlots"];

        // If we selected a ScheduleConfiguration to reload, then do not merge and update slots
        if (_.isUndefined($rootScope.slots) == false) {
            _.each($scope.slots, function (initialSlot) {
                var maybeSlot2 = _.find($rootScope.slots, function (slot2) {
                    return slot2.id == initialSlot.id;
                });
                if (_.isUndefined(maybeSlot2) == false) {
                    if (_.isUndefined(maybeSlot2.proposal) == false) {
                        initialSlot.proposal = maybeSlot2.proposal;
                    }
                }
            });
        } else {
            console.log("No schedule configuration loaded");
            $rootScope.available = $scope.slots.length;
        }
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
            } else {
                $rootScope.available--
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

            // Remove from right
            maybeSlot.proposal=undefined;

            // Add back to left
            $scope.approvedTalks = $scope.approvedTalks.concat(talk);
            $rootScope.available++
        }
    };

    $scope.saveAllocation=function(){
        flash("Allocation for "+$routeParams.confType+" saved");
        SlotService.save({confType: $routeParams.confType}, $scope.slots);
    };

    $scope.isNotAcepted=true;
    $scope.showEmpty=false;
});

reloadScheduleConfController.controller('ReloadScheduleConfController', function ReloadScheduleConfController($location, $rootScope, $scope, $routeParams, ReloadScheduleConf) {
    ReloadScheduleConf.get({id: $routeParams.id}, function (jsonObj){
       $scope.loadedScheduledConfiguration = jsonObj;
        if (_.isUndefined($scope.loadedScheduledConfiguration)) {
            console.log("ERR: conf type not found");
        } else {
            var newConfType = $scope.loadedScheduledConfiguration.confType;
            $rootScope.slots = $scope.loadedScheduledConfiguration.slots;
            $rootScope.available = 0;
            $rootScope.slots.forEach(function(element) {
                if (element.proposal == null) {
                    $rootScope.available++;
                }
            });
            $rootScope.title = "Create a new " + $scope.loadedScheduledConfiguration.confType + " schedule from " + $routeParams.id
            $location.path('/slots').search({confType: newConfType}).replace();
        }
    });

});

deleteSlotController.controller('DeleteSlotController', function DeleteSlotController($routeParams,$location, DeleteScheduledConfiguration,flash ){
    DeleteScheduledConfiguration.delete({id: $routeParams.id}, function () {
        flash("Deleted configuration");
    });
});
