// SPDX-FileCopyrightText: 2026 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr

import spinal.core._
import spinal.lib.bus.amba3.apb.sim.Apb3Driver

object IpIdentificationTest {

  object V0 {
    def checkApi(bus: Apb3Driver, id: SpinalEnumElement[IpIdentification.Ids.type]) {
      val result = bus.read(0)
      val resultApi = (result >> 24) & 0xff
      val resultLength = (result >> 16) & 0xff
      val resultId = IpIdentification.Ids.elements((result & 0xffff).toInt)
      val expectedApi = 0
      val expectedLength = 8
      val expectedId = id
      val expected = expectedApi << 24 | expectedLength << 16 | id.position
      assert(
        result == expected,
        s"IP Identification Header check failed at 0x0:\n" +
          s"    Expected: API=$expectedApi  Length=$expectedLength  ID=$expectedId\n" +
          s"    Received: API=$resultApi  Length=$resultLength  ID=$resultId"
      )
    }

    def checkVersion(bus: Apb3Driver, major: Int, minor: Int, patchlevel: Int) {
      val result = bus.read(4)
      val resultMajor = (result >> 24) & 0xff
      val resultMinor = (result >> 16) & 0xff
      val resultPatchlevel = result & 0xffff
      val expected = (major & 0xff) << 24 | (minor & 0xff) << 16 | (patchlevel & 0xffff)
      assert(
        result == expected,
        s"IP identification Version check failed at 0x4:\n" +
          s"    Expected: Major=$major  Minor=$minor  Patchlevel=$patchlevel\n" +
          s"    Received: Major=$resultMajor  Minor=$resultMinor  Patchlevel=$resultPatchlevel"
      )
    }
  }
}
