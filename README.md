## Nutrition Label Reader

Most nutrition tracking apps are currently inputting macros from food packaging by scanning the UPC barcode and then looking the product up in a database.  That is terrible.  Stop doing that.  Instead, implement this library.  It uses MLKit to OCR in the text of a nutritional label and turn it into macro values that you can track in your app.  This library is free and open source and implementing it is going to take you like, what, one story point?  And then the app will work without an internet connection or a UPC database.

## Usage

Import the library as a module dependency, and that will give you NutritionReaderActivity which you can call with StartActivityForResult or similar.  The library handles permissions if you haven't.  Take a look at the app module in this repo for an example of how to import and use the NutritionReaderActivity


## Future Work

The library currently depends on the label being one macro per line.  Should be a pretty easy fix, I left a TODO of where to fix that

An easy boost to accuracy would happen if the library support fuzzy match on words like protein, fat, etc. to accomodate minor OCR errors.