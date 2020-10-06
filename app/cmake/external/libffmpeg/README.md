## How to cross compile for android

#### Decompress ffmpeg-4.3.1.tar.gz

```
$ tar -zxvf ffmpeg-4.3.1.tar.gz
$ cd ffmpeg-4.3.1
```

#### Link config/android.sh to ffmpeg-4.3.1

> Required NDKr19+

```
$ ln -nsf ../config/android.sh ./
```

#### Configure

```
$ ./android.sh armeabi-v7a
```

#### Build and install

```
$ make clean
$ make -j8
$ make install
```

