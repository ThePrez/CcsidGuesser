# CcsidGuesser
A utility for guessing the CCSID of files (based on file contents)

# Usage

```Raku
Usage: java -jar ccsidguesser.jar  [options] <file>

    Valid options include:
        --show=top/topN/all: how many CCSID guesses to show (default is 'top1', which shows the
                             top 1 result. A value of 'top' shows the top guess and some number
                             of very-near guesses
        --format=<format>:   output format (default is 'ccsid')
        --autofix            automatically and unapologetically change the CCSID tag of the file (IBM i only)

    Valid formats include:
        ccsid:        Show the CCSID only
        enc:          Show the CCSID and encoding name
```
