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


cfpApp.directive("drop", ['$rootScope', function($rootScope) {

  function dragEnter(evt, element, dropStyle) {
    evt.preventDefault();
    element.addClass(dropStyle);
  }

  function dragLeave(evt, element, dropStyle) {
    element.removeClass(dropStyle);
  }

  function dragOver(evt) {
    evt.preventDefault();
  }

  function drop(evt, element, dropStyle) {
    evt.preventDefault();
    element.removeClass(dropStyle);
  }

  return {
    restrict: 'A',
    link: function(scope, element, attrs)  {
      scope.dropData = scope[attrs["drop"]];
      scope.dropStyle = attrs["dropstyle"];
      element.bind('dragenter', function(evt) {
        dragEnter(evt, element, scope.dropStyle);
      });
      element.bind('dragleave', function(evt) {
        dragLeave(evt, element, scope.dropStyle);
      });
      element.bind('dragover', dragOver);
      element.bind('drop', function(evt) {
        drop(evt, element, scope.dropStyle);
        $rootScope.$broadcast('dropEvent', $rootScope.draggedElement, scope.dropData);
      });
    }
  }
}]);