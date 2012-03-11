Very simple tool that consumes all UDP packets it receives. I mainly use it for testing RTP related services where I just need somewhere to send RTP packets that can be counted, logged etc.

## To build

Check it out and then simply type:

    mvn install

## To run it

There is a run.sh script for setting up the class path and start it. However, as of now, I have been lazy and it references the jars based off of my local repository. Need to change this.


