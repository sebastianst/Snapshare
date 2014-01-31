package com.amcgavin.snapshare;
/**
Obfuscator.java created on 12/12/13.

Copyright (C) 2013 Alec McGavin <alec.mcgavin@gmail.com>

This file is part of Snapshare.

Snapshare is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Snapshare is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
a gazillion times. If not, see <http://www.gnu.org/licenses/>.
 */

/** 
 * 
 * This helps with the new obfuscation in snapchat version 4.0.21+
 *
 */
public enum Obfuscator {

    CAMERA_LOAD (new String[] {"refreshFlashButton", "k", "l", "e"}),
    GET_BUS (new String[] {"getInstance", "a", "a", "a"}),
    BUS_POST (new String[] {"post", "c", "c", "c"}),
    M_SNAP_C_EVENT (new String[] {"mSnapCapturedEvent", "w", "w", "v"}),
    M_VIDEO_URI (new String[] {"mVideoUri", "c", "c", "mVideoUri"}),
    ON_BACK_PRESS (new String[] {"onDelegatedBackPress", "m", "m", "c"}),
    BUILDER_CONSTRUCTOR (new String[] {"a", "a", "a", "a"});

    public static final int FOUR_20 = 0;
    public static final int FOUR_21 = 1;
    public static final int FOUR_22 = 2;
    public static final int FOUR_ONE_TEN = 3;

    private String[] v;

    Obfuscator(String[] v) {
        this.v = v;
    }

    /** 
     * Gets the method name to hook
     * @param version snapchat version
     * @return the actual method name
     */
    public String getValue(int version) {
        return this.v[version];
    }
}
