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

$(function() {

    // Search
    var search = function (query) {
        var stream = new EventSource(Router.controllers.Tweetwall.watchTweets(encodeURIComponent(query)).url)



        $(stream).on('message', function (e) {
            var tweet = JSON.parse(e.originalEvent.data);
            if (tweet && tweet.user) {
                  $('#list').prepend($("#tweetTemplate").render(tweet));
                  if($('#list li').length>7) {
                      $('#list').find('li:last-child').remove();
                  }

//                allTweets.push($("#tweetTemplate").render(tweet));
//                var toWrite=allTweets.slice(0,5);
//                 $('#list').empty();
//                _.each(toWrite,function(t){
//                    $('#list').prepend(t);
//                });
//                allTweets.shift();// drop oldest

            }
        })
    };

    $('#query').keypress(function (e) {
        if (e.keyCode == 13) {
            $(this).blur();
            $(this).hide();
            search($(this).val());
        }
    });

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

    startTime();


});