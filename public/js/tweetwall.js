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

$(function () {

    // Search
    var loadTweets = function (query) {
        var stream = new EventSource(Router.controllers.Tweetwall.watchTweets(encodeURIComponent(query)).url)

        $(stream).on('message', function (e) {
            var tweet = JSON.parse(e.originalEvent.data);
            if (tweet && tweet.user) {
                createTweet(tweet);
            }
            if (tweet && tweet.disconnect) {
                console.log("Disconnected");
                console.log(tweet);
            }
        });

        $(stream).on('open', function (e) {
            console.log("Server-sent event connected");
            return false;
        });

        $(stream).on('error', function (e) {
            console.log("Server-sent event error");
            console.log(e);
            if (e.readyState == EventSource.CLOSED) {
                // Connection was closed.
                console.log("event source closed");

            }
            return false;
        });
    };

    var createTweet = function (tweet) {
        var thumImages = [];
        if(tweet.entities && tweet.entities.media) {
            thumImages = _.map(tweet.entities.media,function(m){
                return '<img src="' + m.media_url +'" alt="image">';
            });
            console.log(thumImages);
        }

        var tweetBox = '<li> ' +
            '<img class="tweet-photo" alt="48x48" src="' + tweet.user.profile_image_url + '">' +
            '<span class="sn">' + tweet.user.screen_name +
            '</span> (<span class="un">' + tweet.user.name +
            '</span>)' +
            '<img class="humoricon" src="http://whichlang.appspot.com/posneg?img=true&png=true&text=' +
            encodeURIComponent(tweet.text) +
            '">' +
            '<br>' +
            '<div class="tx">' + tweet.text + '</div>' +
            '</li>';

        var zeList = $('#listTweets');

        var tweetBox2 = $(tweetBox).addClass('new-item');
        zeList.prepend(tweetBox2);

        //$('#listTweets li:nth-child(n+1)').removeClass("new-item");

        if ($('#listTweets li').length >= 8) {
            var lastItem = $('#listTweets li:nth-child(8)');

            $(lastItem).addClass('removed-item').one('webkitAnimationEnd oanimationend msAnimationEnd animationend', function (e) {
                $(this).remove();
            });

        }

    };

    var loadBestTalks = function (query) {
        var stream = new EventSource(Router.controllers.Tweetwall.watchBestTalks().url);

        $(stream).on('message', function (e) {
            var bestTalks = JSON.parse(e.originalEvent.data);
            if (bestTalks) {
                createBestTalkPanels(bestTalks);
            }
        });
    };

    var createBestTalkPanels = function(bestTalks){
        $('#sessionPop').prepend(bestTalks);
    };

    function checkTime(i) {
        if (i < 10) {
            i = "0" + i;
        }
        return i;
    }


    function startTime() {
        var today = new Date();
        var h = today.getHours();
        var m = today.getMinutes();
        var s = today.getSeconds();
        // add a zero in front of numbers<10
        m = checkTime(m);
        s = checkTime(s);
        document.getElementById('wallTime').innerHTML = h + ":" + m + ":" + s;
        t = setTimeout(function () {
            startTime()
        }, 500);
    }


    var init = function () {
        startTime();
        loadTweets("tennis,devoxx,devoxxfr,cfp.devoxx.fr,www.devoxx.fr,devoxx.fr,devoxxuk"); // the keyword, the hashtag to stream
        loadBestTalks();
    };

    init();

});