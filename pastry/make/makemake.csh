#! /usr/bin/csh -f

set file        = Makefile 

set cwd         = `pwd`

set pattern     = `echo $JAVA_DEV_ROOT/src/ | sed "s/\//\\\//g"`

set package_loc = `echo $cwd | sed "s/${pattern}//g"`
set package     = `echo $package_loc | sed "s/\//\./g"`

rm -f $file
touch $file

echo "PACKAGE     = $package" >> $file

echo '' >> $file

echo 'SOURCE      = \' >> $file

foreach sourcefile (`ls *.java *.gif *.jpg`)
    echo "    ${sourcefile} \" >> $file
end

echo '' >> $file
echo 'RMI_SOURCE  =' >> $file
echo '' >> $file
echo 'MAIN        =' >> $file
echo '' >> $file
echo 'include $(JAVA_DEV_ROOT)/Makefile' >> $file
echo '' >> $file
