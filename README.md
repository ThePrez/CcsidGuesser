# CcsidGuesser
A utility for guessing the CCSID of files (based on file contents)

# Usage

```Raku
Usage: java -jar ccsidguesser.jar  [options] <file>

    Valid options include:
        --show=top/topN/all: how many CCSID guesses to show (default is 'top1'), which shows the
                             top 1 result. A value of 'top' shows the top guess and some number
                             of very-near guesses
        --format=<format>:   output format (default is 'ccsid')
        --autofix            automatically and unapologetically change the CCSID tag of the file
                             to match the top guess for the file's contents (IBM i only)

    Valid formats include:
        ccsid:        Show the CCSID only
        enc:          Show the CCSID and encoding name
```

# Installation

Simply download the latest `ccsidguesser.jar` from [the Releases page](https://github.com/ThePrez/CcsidGuesser/releases).

# Examples

Show the best guess CCSID for `myfile.txt`:
```
java -jar ccsidguesser.jar myfile.txt
```

Show the top 5 guesses for the CCSID and their corresponding encoding names:
```
java -jar ccsidguesser.jar myfile.txt  --show=top5 --format=enc
```

Automatically set `myfile.txt`'s CCSID tag to match the best guess for the file's contents:
```
java -jar ccsidguesser.jar myfile.txt  --show=top5 --format=enc
```
