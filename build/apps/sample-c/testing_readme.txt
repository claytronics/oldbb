How to use the Test programs:

SET ID:
setID_ensemble.bb and setID_single.bb are the programs for changing the blinky blocks id.setID_single.bb is for only one block and setID_ensemble.bb is for
several blocks. When you use setID_all.bb you have to line up every block so that you can set their id depending on the first block
id (the one connected to the host).Both of the program use the logger so uncomment the LOG_DEBUG flags into the Makefile.
you can enable this test mode by adding "-t"when starting the logger, the logger program will provide instructions.

COLOR AND ACCELEROMETER:
color_accel_tester.bb is the program used for testing color and accelerometer. 
To see the result you have to use the logger,so don't forget to uncomment the LOG_DEBUG flags into the makefile before compiling the program. 
You can enable this test mode by adding "-t" when starting the logger, the logger program will provide instructions.

NETWORK:
network_test_ensemble.bb is the program for testing the blinky block networks. 
This program will create a spanning tree, when the spanning tree is set up, all the blocks will turn Blue. 
The root block is the one connected to the host, it will send a message to all the spanning tree leaves. 
Then the leaves will send back a message to the root. Once the root receives all the message from the leaves, one cycle is complete.
The test suceed if we reach 100 (you can change this number by changing MAX_CYCLE).
In case of success or failure, a log message is sent to the host, the user can then judge the quality of the communications.





