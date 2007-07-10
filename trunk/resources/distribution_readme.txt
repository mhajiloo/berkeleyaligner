#############################
# The Berkeley Word Aligner #
#############################

The Berkeley Word Aligner is a statistical machine translation tool that automatically aligns words in a sentence-aligned parallel corpus.

-------------
Install & Run
-------------

No installation is required beyond expanding the distribution archive.  

To train the aligner on the provided sample data, run the following command:

  ./align example.conf

This invokation can also be identically achieved via the command:

  java -server -mx200m -jar berkeleyaligner.jar ++example.conf

Configuration can also be set via the command line rather than a conf file:

  java -server -mx200m -jar berkeleyaligner.jar -execDir output -create

For a list of available options and their default values, try:

  java -jar berkeleyaligner.jar -help

The documentation directory contains further information on configuration options.

-----------
Information
-----------

For more information about the package as a whole, please visit:

  http://nlp.cs.berkeley.edu/pages/wordaligner.html

The source code for this project, along with further information and resources, is available online:

  http://code.google.com/p/berkeleyaligner

Enjoy!

============================================================
(C) Copyright 2007, John DeNero and Percy Liang

http://www.cs.berkeley.edu/~denero
http://www.cs.berkeley.edu/~pliang
http://nlp.cs.berkeley.edu

Permission is granted for anyone to copy, use, or modify these programs and
accompanying documents for purposes of research or education, provided this
copyright notice is retained, and note is made of any changes that have been
made.

These programs and documents are distributed without any warranty, express or
implied.  As the programs were written for research purposes only, they have
not been tested to the degree that would be advisable in any important
application.  All use of these programs is entirely at the user's own risk.
============================================================