/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 Association du Paris Java User Group.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

(function(){
    var TOP_TALKS_URL = '/api/conferences/DV15/topFavedTalks?limit=10';
    var CATEGORIES_URL = 'https://api-voting.devoxx.com/DV15/categories';

    var TopTalks = React.createClass({displayName: "Top Faved Talks",
        getInitialState: function(){
            return {
                title: this.props.title,
                loadingTalks: true,
                error: this.props.error || "",
                talks: [],
                url: this.props.url || TOP_TALKS_URL,
                refreshInterval: parseInt(this.props.refreshInterval) || 60*1000
            };
        },
        componentWillReceiveProps: function(nextProps) {
            this.setState(nextProps);
        },
        render: function(){
            return (
                React.createElement("div", {className: "top-list-container"},
                    React.createElement("div", {className: "page-header"},
                        React.createElement("img", {src: "http://devoxxuk.github.io/conf-2015/img/devoxx_logo.gif", alt: "Devoxx"}),
                        React.createElement("h1", null, this.state.title, " Top faved talks")
                    ),
                    React.createElement("div", {className: "talks-container"},
                        React.createElement(TalksContainer, {loadingTalks: this.state.loadingTalks, talks: this.state.talks, error: this.state.error})
                    )
                )
            );
        },
        getTalks: function(){
            var url = this.state.url,
                refresh = this.refresh,
                error = this.handleError,
                render = this.handleSuccess;
            $.ajax({
                url: url,
                type: "GET",
                timeout: 10*1000,
                dataType: "json"
            }).done(function(data){
                render(data);
                refresh();
            }).fail(function(jqXHR, textStatus, errorThrown) {
                error(jqXHR, textStatus, errorThrown);
                refresh();
            });
        },
        refresh: function(){
            setTimeout(this.getTalks, this.state.refreshInterval);
        },
        handleSuccess: function(data){
            this.setProps({ loadingTalks: false, talks: data.talks, error: ''});
        },
        handleError: function(jqXHR, textStatus, errorThrown) {
            console.log(jqXHR);
            this.setProps({ error: "Oops... (" + jqXHR.status + ") " + textStatus + ": " + errorThrown, loadingTalks: false });
        },
        componentDidMount: function(){
            this.getTalks();
        }
    });

    var TalksContainer = React.createClass({displayName: "TalksContainer",
        getInitialState: function() {
            return {
                loadingTalks: this.props.loadingTalks === "true" || false,
                error: this.props.error || "",
                talks: []
            }
        },
        componentWillReceiveProps: function(nextProps) {
            this.setState(nextProps);
        },
        render: function() {
            var loadingTalks = this.state.loadingTalks ? React.createElement("div", {className: "alert alert-warning", id: "loading-talks-notification"}, "Loading talks...") : '';
            var error = this.state.error === '' ? '' : React.createElement("div", {className: "alert alert-danger", id: "error-notification"}, this.state.error);
            var loaded = this.state.loadingTalks === false;
            var table = loaded ? React.createElement(Talks, {details: this.state.talks, error: this.state.error, key: "devoxx-top-talks"}) : '';
            return (
              React.createElement("div", null,
                loadingTalks,
                error,
                table,
                React.createElement("p", {className: "text-center text-muted"}, React.createElement("small", null, "This table will frequently reload the results automatically (and recover from network errors)."))
              )
            );
        }
    });

    var Talks = React.createClass({displayName: "Talks",
        shouldComponentUpdate: function(nextProps){
            return nextProps.error === '';
        },
        render: function(){
          var talks = _.map(this.props.details, function(talk, idx){
            return React.createElement(Talk, {rowNum: idx, details: talk, key: 'devoxx-talk-' + talk.name});
          });
          var tbody = _.isEmpty(talks) ? React.createElement(NoTalks, null) : talks;
          return (
            React.createElement("table", {className: "table table-striped"},
              React.createElement("thead", null,
                React.createElement("tr", null,
                  React.createElement("th", null, "#"),
                  React.createElement("th", null, "Title"),
                  React.createElement("th", null, "Speakers"),
                  React.createElement("th", {className: "devoxx-talk-type"}, "Talk Type"),
                  React.createElement("th", {className: "devoxx-track"}, "Track"),
                  React.createElement("th", null, "Avg Vote"),
                  React.createElement("th", {className: "devoxx-num-votes"}, "# Votes")
                )
              ),
              React.createElement("tbody", null,
                tbody
              )
            )
          );
        }
    });

    var Talk = React.createClass({displayName: "Talk",
        getInitialState: function(){
            return {
                rowNum: this.props.rowNum,
                details: this.props.details,
                className: ''
            };
        },
        componentWillReceiveProps: function(nextProps) {
            this.setState(nextProps);
        },
        render: function(){
            var talk = this.state.details,
                idx = this.state.rowNum;
            return (
              React.createElement("tr", {className: this.state.className},
                React.createElement("td", null, parseInt(idx) + 1),
                React.createElement("td", null, talk.title),
                React.createElement("td", null, talk.speakers.join(', ')),
                React.createElement("td", {className: "devoxx-talk-type"}, talk.type),
                React.createElement("td", {className: "devoxx-track"}, talk.track),
                React.createElement("td", null, Math.round(talk.avg * 10)/10),
                React.createElement("td", {className: "devoxx-num-votes"}, talk.count)
              )
            );
        }
    });

    var NoTalks = React.createClass({displayName: "NoTalks",
        render: function(){
            return (
                React.createElement("tr", {className: "warning"},
                    React.createElement("td", {colSpan: "7", className: "text-center"}, "Sorry, there aren't any votes yet!")
                )
            );
        }
    });

    function capitalizeFirstLetter(string) {
        return string.charAt(0).toUpperCase() + string.slice(1);
    }

    function createTopTalksTable(key, title, url) {
        if (document.getElementById(key)) {
            console.error("The key '" + key + "' is already in use");
            return;
        }
        $("#main").append(
            $("<div></div>", {
                id: key
            })
        );
        React.render(React.createElement(TopTalks, {key: key, title: title, url: url}), document.getElementById(key));
    }

    console.log("Here we go");

    createTopTalksTable('devoxx-top-talks', '2015', TOP_TALKS_URL);

    _.forEach(['monday', 'tuesday', 'wednesday', 'thursday', 'friday'], function(dow){
        createTopTalksTable(
            'devoxx-top-talks' + dow,
            '2015 ' + capitalizeFirstLetter(dow) + "'s",
            TOP_TALKS_URL + "&day=" + dow
        );
    });

    $.ajax({
        url: CATEGORIES_URL,
        type: "GET",
        timeout: 10*1000,
        dataType: "json"
    }).done(function(data){
        if (data.tracks) {
            _.forEach(_.sortBy(data.tracks), function(track, idx){
                createTopTalksTable(
                    'devoxx-top-talks-track-' + idx,
                    "2015 '" + track + "'",
                    TOP_TALKS_URL + "&track=" + encodeURIComponent(track)
                );
            });
        }
        if (data.talkTypes) {
            _.forEach(_.sortBy(data.talkTypes), function(type, idx){
                createTopTalksTable(
                    'devoxx-top-talks-type-' + idx,
                    "2015 '" + type + "'",
                    TOP_TALKS_URL + "&talkType=" + encodeURIComponent(type)
                );
            });
        }
    }).fail(function(){
        console.error("Retrieving categories failed!");
    });

})();