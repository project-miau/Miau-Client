/*
 * Copyright (c) 2002-2008 LWJGL Project
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'LWJGL' nor the names of
 *   its contributors may be used to endorse or promote products derived
 *   from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
* Portions Copyright (C) 2003-2006 Sun Microsystems, Inc.
* All rights reserved.
*/

/*
** License Applicability. Except to the extent portions of this file are
** made subject to an alternative license as permitted in the SGI Free
** Software License B, Version 1.1 (the "License"), the contents of this
** file are subject only to the provisions of the License. You may not use
** this file except in compliance with the License. You may obtain a copy
** of the License at Silicon Graphics, Inc., attn: Legal Services, 1600
** Amphitheatre Parkway, Mountain View, CA 94043-1351, or at:
**
** http://oss.sgi.com/projects/FreeB
**
** Note that, as provided in the License, the Software is distributed on an
** "AS IS" basis, with ALL EXPRESS AND IMPLIED WARRANTIES AND CONDITIONS
** DISCLAIMED, INCLUDING, WITHOUT LIMITATION, ANY IMPLIED WARRANTIES AND
** CONDITIONS OF MERCHANTABILITY, SATISFACTORY QUALITY, FITNESS FOR A
** PARTICULAR PURPOSE, AND NON-INFRINGEMENT.
**
** NOTE:  The Original Code (as defined below) has been licensed to Sun
** Microsystems, Inc. ("Sun") under the SGI Free Software License B
** (Version 1.1), shown above ("SGI License").   Pursuant to Section
** 3.2(3) of the SGI License, Sun is distributing the Covered Code to
** you under an alternative license ("Alternative License").  This
** Alternative License includes all of the provisions of the SGI License
** except that Section 2.2 and 11 are omitted.  Any differences between
** the Alternative License and the SGI License are offered solely by Sun
** and not by SGI.
**
** Original Code. The Original Code is: OpenGL Sample Implementation,
** Version 1.2.1, released January 26, 2000, developed by Silicon Graphics,
** Inc. The Original Code is Copyright (c) 1991-2000 Silicon Graphics, Inc.
** Copyright in any portions created by third parties is as indicated
** elsewhere herein. All Rights Reserved.
**
** Additional Notice Provisions: The application programming interfaces
** established by SGI in conjunction with the Original Code are The
** OpenGL(R) Graphics System: A Specification (Version 1.2.1), released
** April 1, 1999; The OpenGL(R) Graphics System Utility Library (Version
** 1.3), released November 4, 1998; and OpenGL(R) Graphics with the X
** Window System(R) (Version 1.3), released October 19, 1998. This software
** was created using the OpenGL(R) version 1.2.1 Sample Implementation
** published by SGI, but has not been independently verified as being
** compliant with the OpenGL(R) version 1.2.1 Specification.
**
** Author: Eric Veach, July 1994
** Java Port: Pepijn Van Eeckhoudt, July 2003
** Java Port: Nathan Parker Burg, August 2003
*/
package org.lwjgl.util.glu.tessellation;

class Mesh {
    private Mesh() {
    }

     




    static GLUhalfEdge MakeEdge(GLUhalfEdge eNext) {
        GLUhalfEdge e;
        GLUhalfEdge eSym;
        GLUhalfEdge ePrev;






        e = new GLUhalfEdge(true);

        eSym = new GLUhalfEdge(false);


         
        if (!eNext.first) {
            eNext = eNext.Sym;
        }

        


        ePrev = eNext.Sym.next;
        eSym.next = ePrev;
        ePrev.Sym.next = e;
        e.next = eNext;
        eNext.Sym.next = eSym;

        e.Sym = eSym;
        e.Onext = e;
        e.Lnext = eSym;
        e.Org = null;
        e.Lface = null;
        e.winding = 0;
        e.activeRegion = null;

        eSym.Sym = e;
        eSym.Onext = eSym;
        eSym.Lnext = e;
        eSym.Org = null;
        eSym.Lface = null;
        eSym.winding = 0;
        eSym.activeRegion = null;

        return e;
    }







    static void Splice(GLUhalfEdge a, GLUhalfEdge b) {
        GLUhalfEdge aOnext = a.Onext;
        GLUhalfEdge bOnext = b.Onext;

        aOnext.Sym.Lnext = b;
        bOnext.Sym.Lnext = a;
        a.Onext = bOnext;
        b.Onext = aOnext;
    }







    static void MakeVertex(GLUvertex newVertex,
                           GLUhalfEdge eOrig, GLUvertex vNext) {
        GLUhalfEdge e;
        GLUvertex vPrev;
        GLUvertex vNew = newVertex;

        assert (vNew != null);

         
        vPrev = vNext.prev;
        vNew.prev = vPrev;
        vPrev.next = vNew;
        vNew.next = vNext;
        vNext.prev = vNew;

        vNew.anEdge = eOrig;
        vNew.data = null;
         

         
        e = eOrig;
        do {
            e.Org = vNew;
            e = e.Onext;
        } while (e != eOrig);
    }







    static void MakeFace(GLUface newFace, GLUhalfEdge eOrig, GLUface fNext) {
        GLUhalfEdge e;
        GLUface fPrev;
        GLUface fNew = newFace;

        assert (fNew != null);

         
        fPrev = fNext.prev;
        fNew.prev = fPrev;
        fPrev.next = fNew;
        fNew.next = fNext;
        fNext.prev = fNew;

        fNew.anEdge = eOrig;
        fNew.data = null;
        fNew.trail = null;
        fNew.marked = false;

        


        fNew.inside = fNext.inside;

         
        e = eOrig;
        do {
            e.Lface = fNew;
            e = e.Lnext;
        } while (e != eOrig);
    }




    static void KillEdge(GLUhalfEdge eDel) {
        GLUhalfEdge ePrev, eNext;

         
        if (!eDel.first) {
            eDel = eDel.Sym;
        }

         
        eNext = eDel.next;
        ePrev = eDel.Sym.next;
        eNext.Sym.next = ePrev;
        ePrev.Sym.next = eNext;
    }





    static void KillVertex(GLUvertex vDel, GLUvertex newOrg) {
        GLUhalfEdge e, eStart = vDel.anEdge;
        GLUvertex vPrev, vNext;

         
        e = eStart;
        do {
            e.Org = newOrg;
            e = e.Onext;
        } while (e != eStart);

         
        vPrev = vDel.prev;
        vNext = vDel.next;
        vNext.prev = vPrev;
        vPrev.next = vNext;
    }




    static void KillFace(GLUface fDel, GLUface newLface) {
        GLUhalfEdge e, eStart = fDel.anEdge;
        GLUface fPrev, fNext;

         
        e = eStart;
        do {
            e.Lface = newLface;
            e = e.Lnext;
        } while (e != eStart);

         
        fPrev = fDel.prev;
        fNext = fDel.next;
        fNext.prev = fPrev;
        fPrev.next = fNext;
    }


     




    public static GLUhalfEdge __gl_meshMakeEdge(GLUmesh mesh) {
        GLUvertex newVertex1 = new GLUvertex();
        GLUvertex newVertex2 = new GLUvertex();
        GLUface newFace = new GLUface();
        GLUhalfEdge e;

        e = MakeEdge(mesh.eHead);
        if (e == null) return null;

        MakeVertex(newVertex1, e, mesh.vHead);
        MakeVertex(newVertex2, e.Sym, mesh.vHead);
        MakeFace(newFace, e, mesh.fHead);
        return e;
    }

























    public static boolean __gl_meshSplice(GLUhalfEdge eOrg, GLUhalfEdge eDst) {
        boolean joiningLoops = false;
        boolean joiningVertices = false;

        if (eOrg == eDst) return true;

        if (eDst.Org != eOrg.Org) {
             
            joiningVertices = true;
            KillVertex(eDst.Org, eOrg.Org);
        }
        if (eDst.Lface != eOrg.Lface) {
             
            joiningLoops = true;
            KillFace(eDst.Lface, eOrg.Lface);
        }

         
        Splice(eDst, eOrg);

        if (!joiningVertices) {
            GLUvertex newVertex = new GLUvertex();

            


            MakeVertex(newVertex, eDst, eOrg.Org);
            eOrg.Org.anEdge = eOrg;
        }
        if (!joiningLoops) {
            GLUface newFace = new GLUface();

            


            MakeFace(newFace, eDst, eOrg.Lface);
            eOrg.Lface.anEdge = eOrg;
        }

        return true;
    }












    static boolean __gl_meshDelete(GLUhalfEdge eDel) {
        GLUhalfEdge eDelSym = eDel.Sym;
        boolean joiningLoops = false;

        


        if (eDel.Lface != eDel.Sym.Lface) {
             
            joiningLoops = true;
            KillFace(eDel.Lface, eDel.Sym.Lface);
        }

        if (eDel.Onext == eDel) {
            KillVertex(eDel.Org, null);
        } else {
             
            eDel.Sym.Lface.anEdge = eDel.Sym.Lnext;
            eDel.Org.anEdge = eDel.Onext;

            Splice(eDel, eDel.Sym.Lnext);
            if (!joiningLoops) {
                GLUface newFace = new GLUface();

                 
                MakeFace(newFace, eDel, eDel.Lface);
            }
        }

        


        if (eDelSym.Onext == eDelSym) {
            KillVertex(eDelSym.Org, null);
            KillFace(eDelSym.Lface, null);
        } else {
             
            eDel.Lface.anEdge = eDelSym.Sym.Lnext;
            eDelSym.Org.anEdge = eDelSym.Onext;
            Splice(eDelSym, eDelSym.Sym.Lnext);
        }

         
        KillEdge(eDel);

        return true;
    }


     










    static GLUhalfEdge __gl_meshAddEdgeVertex(GLUhalfEdge eOrg) {
        GLUhalfEdge eNewSym;
        GLUhalfEdge eNew = MakeEdge(eOrg);

        eNewSym = eNew.Sym;

         
        Splice(eNew, eOrg.Lnext);

         
        eNew.Org = eOrg.Sym.Org;
        {
            GLUvertex newVertex = new GLUvertex();

            MakeVertex(newVertex, eNewSym, eNew.Org);
        }
        eNew.Lface = eNewSym.Lface = eOrg.Lface;

        return eNew;
    }






    public static GLUhalfEdge __gl_meshSplitEdge(GLUhalfEdge eOrg) {
        GLUhalfEdge eNew;
        GLUhalfEdge tempHalfEdge = __gl_meshAddEdgeVertex(eOrg);

        eNew = tempHalfEdge.Sym;

         
        Splice(eOrg.Sym, eOrg.Sym.Sym.Lnext);
        Splice(eOrg.Sym, eNew);

         
        eOrg.Sym.Org = eNew.Org;
        eNew.Sym.Org.anEdge = eNew.Sym;	 
        eNew.Sym.Lface = eOrg.Sym.Lface;
        eNew.winding = eOrg.winding;	 
        eNew.Sym.winding = eOrg.Sym.winding;

        return eNew;
    }












    static GLUhalfEdge __gl_meshConnect(GLUhalfEdge eOrg, GLUhalfEdge eDst) {
        GLUhalfEdge eNewSym;
        boolean joiningLoops = false;
        GLUhalfEdge eNew = MakeEdge(eOrg);

        eNewSym = eNew.Sym;

        if (eDst.Lface != eOrg.Lface) {
             
            joiningLoops = true;
            KillFace(eDst.Lface, eOrg.Lface);
        }

         
        Splice(eNew, eOrg.Lnext);
        Splice(eNewSym, eDst);

         
        eNew.Org = eOrg.Sym.Org;
        eNewSym.Org = eDst.Org;
        eNew.Lface = eNewSym.Lface = eOrg.Lface;

         
        eOrg.Lface.anEdge = eNewSym;

        if (!joiningLoops) {
            GLUface newFace = new GLUface();

             
            MakeFace(newFace, eNew, eOrg.Lface);
        }
        return eNew;
    }


     








    static void __gl_meshZapFace(GLUface fZap) {
        GLUhalfEdge eStart = fZap.anEdge;
        GLUhalfEdge e, eNext, eSym;
        GLUface fPrev, fNext;

         
        eNext = eStart.Lnext;
        do {
            e = eNext;
            eNext = e.Lnext;

            e.Lface = null;
            if (e.Sym.Lface == null) {
                 

                if (e.Onext == e) {
                    KillVertex(e.Org, null);
                } else {
                     
                    e.Org.anEdge = e.Onext;
                    Splice(e, e.Sym.Lnext);
                }
                eSym = e.Sym;
                if (eSym.Onext == eSym) {
                    KillVertex(eSym.Org, null);
                } else {
                     
                    eSym.Org.anEdge = eSym.Onext;
                    Splice(eSym, eSym.Sym.Lnext);
                }
                KillEdge(e);
            }
        } while (e != eStart);

         
        fPrev = fZap.prev;
        fNext = fZap.next;
        fNext.prev = fPrev;
        fPrev.next = fNext;
    }





    public static GLUmesh __gl_meshNewMesh() {
        GLUvertex v;
        GLUface f;
        GLUhalfEdge e;
        GLUhalfEdge eSym;
        GLUmesh mesh = new GLUmesh();

        v = mesh.vHead;
        f = mesh.fHead;
        e = mesh.eHead;
        eSym = mesh.eHeadSym;

        v.next = v.prev = v;
        v.anEdge = null;
        v.data = null;

        f.next = f.prev = f;
        f.anEdge = null;
        f.data = null;
        f.trail = null;
        f.marked = false;
        f.inside = false;

        e.next = e;
        e.Sym = eSym;
        e.Onext = null;
        e.Lnext = null;
        e.Org = null;
        e.Lface = null;
        e.winding = 0;
        e.activeRegion = null;

        eSym.next = eSym;
        eSym.Sym = e;
        eSym.Onext = null;
        eSym.Lnext = null;
        eSym.Org = null;
        eSym.Lface = null;
        eSym.winding = 0;
        eSym.activeRegion = null;

        return mesh;
    }





    static GLUmesh __gl_meshUnion(GLUmesh mesh1, GLUmesh mesh2) {
        GLUface f1 = mesh1.fHead;
        GLUvertex v1 = mesh1.vHead;
        GLUhalfEdge e1 = mesh1.eHead;
        GLUface f2 = mesh2.fHead;
        GLUvertex v2 = mesh2.vHead;
        GLUhalfEdge e2 = mesh2.eHead;

         
        if (f2.next != f2) {
            f1.prev.next = f2.next;
            f2.next.prev = f1.prev;
            f2.prev.next = f1;
            f1.prev = f2.prev;
        }

        if (v2.next != v2) {
            v1.prev.next = v2.next;
            v2.next.prev = v1.prev;
            v2.prev.next = v1;
            v1.prev = v2.prev;
        }

        if (e2.next != e2) {
            e1.Sym.next.Sym.next = e2.next;
            e2.next.Sym.next = e1.Sym.next;
            e2.Sym.next.Sym.next = e1;
            e1.Sym.next = e2.Sym.next;
        }

        return mesh1;
    }




    static void __gl_meshDeleteMeshZap(GLUmesh mesh) {
        GLUface fHead = mesh.fHead;

        while (fHead.next != fHead) {
            __gl_meshZapFace(fHead.next);
        }
        assert (mesh.vHead.next == mesh.vHead);
    }



    public static void __gl_meshDeleteMesh(GLUmesh mesh) {
        GLUface f, fNext;
        GLUvertex v, vNext;
        GLUhalfEdge e, eNext;

        for (f = mesh.fHead.next; f != mesh.fHead; f = fNext) {
            fNext = f.next;
        }

        for (v = mesh.vHead.next; v != mesh.vHead; v = vNext) {
            vNext = v.next;
        }

        for (e = mesh.eHead.next; e != mesh.eHead; e = eNext) {
             
            eNext = e.next;
        }
    }



    public static void __gl_meshCheckMesh(GLUmesh mesh) {
        GLUface fHead = mesh.fHead;
        GLUvertex vHead = mesh.vHead;
        GLUhalfEdge eHead = mesh.eHead;
        GLUface f, fPrev;
        GLUvertex v, vPrev;
        GLUhalfEdge e, ePrev;

        fPrev = fHead;
        for (fPrev = fHead; (f = fPrev.next) != fHead; fPrev = f) {
            assert (f.prev == fPrev);
            e = f.anEdge;
            do {
                assert (e.Sym != e);
                assert (e.Sym.Sym == e);
                assert (e.Lnext.Onext.Sym == e);
                assert (e.Onext.Sym.Lnext == e);
                assert (e.Lface == f);
                e = e.Lnext;
            } while (e != f.anEdge);
        }
        assert (f.prev == fPrev && f.anEdge == null && f.data == null);

        vPrev = vHead;
        for (vPrev = vHead; (v = vPrev.next) != vHead; vPrev = v) {
            assert (v.prev == vPrev);
            e = v.anEdge;
            do {
                assert (e.Sym != e);
                assert (e.Sym.Sym == e);
                assert (e.Lnext.Onext.Sym == e);
                assert (e.Onext.Sym.Lnext == e);
                assert (e.Org == v);
                e = e.Onext;
            } while (e != v.anEdge);
        }
        assert (v.prev == vPrev && v.anEdge == null && v.data == null);

        ePrev = eHead;
        for (ePrev = eHead; (e = ePrev.next) != eHead; ePrev = e) {
            assert (e.Sym.next == ePrev.Sym);
            assert (e.Sym != e);
            assert (e.Sym.Sym == e);
            assert (e.Org != null);
            assert (e.Sym.Org != null);
            assert (e.Lnext.Onext.Sym == e);
            assert (e.Onext.Sym.Lnext == e);
        }
        assert (e.Sym.next == ePrev.Sym
                && e.Sym == mesh.eHeadSym
                && e.Sym.Sym == e
                && e.Org == null && e.Sym.Org == null
                && e.Lface == null && e.Sym.Lface == null);
    }
}
