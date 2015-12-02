# How to reset your CFP and prepare the Database for a new year ?

This document describes how to upgrade and to prepare your CFP for a new edition of the conference. 

# Do a backup of the HTML program

First, if you used the Publisher component, you should do a backup of all your program pages. To do so, we recommend that you use [wget](http://linuxreviews.org/quicktips/wget/) with a recursive option. 
 
For e.g., to save the Devoxx France 2015 content : 

    wget -r http://cfp.devoxx.fr/2015/index.html
    ...
    ...
    
    cfp.devoxx.fr/2015/talk/FAA-6366/Participez_ 100%[================================================================================================>]   5.79K  --.-KB/s   in 0.003s 
    
    2015-10-17 10:41:53 (2.18 MB/s) - 'cfp.devoxx.fr/2015/talk/FAA-6366/Participez_a_JHipster' saved [5930/5930]
    
    --2015-10-17 10:41:53--  http://cfp.devoxx.fr/2015/speaker/blog.boulay.eu
    Reusing existing connection to cfp.devoxx.fr:80.
    HTTP request sent, awaiting response... 404 Not Found
    2015-10-17 10:41:53 ERROR 404: Not Found.
    
    FINISHED --2015-10-17 10:41:53--
    Total wall clock time: 42s
    Downloaded: 501 files, 5.0M in 3.8s (1.31 MB/s)
    
You can then upload the set of HTML files to a Wordpress/static web server. The Publisher URLs are defined with .html so that the content is easy to scrape.
    
# Keep a backup version of your Redis database

Before performing any operation from the Admin panel, make a copy of your Redis dump file and the AOF file, just in case...

Run redis-server and redis-client (see sections ... in README.md). Once they are running, SYNC them, and run SAVE or BGSAVE them. please readup and refer to this resource http://zdk.blinkenshell.org/redis-backup-and-restore/.

# Create a tag on Github

On the dev branch, create a tag such as `DevoxxFR_2015_Backup`, in order to be able to compare between any two years. Here are the steps to go about:

Update your branch locally or on your remote organisation via:
```
$ git checkout [dev-...]
$ git pull upstream dev-[...]
$ git push origin dev-[...]
 ```
Here ```upstream``` is organisor's repo, ```origin``` is your own copy (fork of the organisor's repo). 
```
$ git tag Devoxx[xx]_[year]_backup
$ git push upstream dev-[...]
$ git push origin dev-[...]
```

 Create a tag for the 




# Start with the configuration 

Update `ConferenceDescriptor.scala` and start to edit the year for the new year. Check if your branch needs to be updated from another branch usually its the ```dev``` branch on bitbucket (but can also be github). Sometimes this might not be necessary as the branches might be different as changes may not be relevant.

# Edit the messages

Edit and replace the year with the new year, in the conf/messages.properties text file. Refer to the above section about updating your branch before doing this. 

# Archive talks and reviews

As a super-admin, go to `/cfpadmin/attic/home` 

The Attic is a specific part of the CFP that has been designed to archive all talks and reviews. This is deliberately not part of the admin navigation bar. Only a super-admin with a deep knowledge of the CFP should use this section.

As explained, you should have a backup of your Redis database somewhere, before you start to trigger the clean-up.
Click on each button, and wait for completion.

Once completed, the program is unpublished, each speaker sees the list of talks as "archived". You can also reset the list of invited speakers.

# Remove agenda

Go to the [Angular Schedule application](/assets/angular/index.html) and delete all the schedules. Here are the steps to go through the process successfully:

1) click on « Backoffice » top nav button, this redirect you to http://cfp.devoxx.[...]/admin
2) select Angular App (the green button) - http://cfp.devoxx.[...]/assets/angular/index.html#/
3) click « Reload a saved configuration of slots/talks »
4) click on delete for each element
5) repeat 3 and 4 until the list is empty

You should do that AFTER the CFP web site is saved (http://cfp.devoxx..[...]/[year]/index.html) with wget (as explained above).

# Reset wishlist

Go to the Wishlist panel and delete all wishlist items. This option might NOT apply to you, so not an issue if you dont see this option in the Admin panel or Attic or BackOffice.

You're good to go!

# Side note 

Any contribution and better documentation on this process is welcome.
Please update or correct this document with a Pull-Request. 
