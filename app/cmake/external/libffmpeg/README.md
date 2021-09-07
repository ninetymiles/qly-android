## How to cross compile for android

#### Decompress

```
$ tar -zxvf ffmpeg-4.3.1.tar.gz
$ cd ffmpeg-4.3.1
```

#### Patch

```
$ patch -f -N -s -p1 -d . < ../patches/001_ld_soname.patch
```

#### Configure

> Required NDKr19+

```
$ ln -nsf ../config/android.sh ./
$ ./android.sh armeabi-v7a
```

#### Build and install

```
$ make clean
$ make -j8
$ make install
```

