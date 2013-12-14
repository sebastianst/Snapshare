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
    
    refreshFlashButton ("refreshFlashButton", "k"),
    busGetInstance ("getInstance", "a"),
    busPost ("post", "c");
    
    private String pre;
    private String post;
    Obfuscator(String pre, String post) {
        this.pre = pre;
        this.post = post;
    }
    
    public String getValue() {
        if (net.cantab.stammler.snapshare.Snapshare.POST) {
            return this.post;
        }
        else return this.pre;
    }

}
