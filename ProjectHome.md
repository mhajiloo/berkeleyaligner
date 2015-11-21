The BerkeleyAligner is a word alignment software package that implements recent innovations in unsupervised word alignment.

### News ###

**9/28/09** As of release 2.1, we have split the Berkeley aligner into two downloads.  The unsupervised aligner doesn't require a set of hand-labeled word alignments.  The supervised aligner does, and it depends on the unsupervised aligner.

### Recent changes and bug fixes ###

**9/28** You can now run the unsupervised aligner without a hand-aligned test set; the evaluation phase will be skipped.

**9/28** Loading trained models for evaluation only now works correctly (just give an empty training sequence)

**9/28** Output can now be split into multiple alignment files corresponding to multiple input files (alignInputsSeparately option)

**9/28** The test set does not need to be included in the training sets