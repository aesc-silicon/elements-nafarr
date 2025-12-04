// SPDX-FileCopyrightText: 2025 aesc silicon
//
// SPDX-License-Identifier: CERN-OHL-W-2.0

package nafarr

import spinal.core._

object CheckTester{
  def checkFailure(func : => Unit) : Boolean = {
    try{func} catch {
      case e: Throwable => {
        print(e)
        return true
      }
    }
    return false
  }

  def generationShouldFail(gen : => Component): Unit ={
    assert(checkFailure{SpinalVhdl(gen)})
    assert(checkFailure{SpinalVerilog(gen)})
  }

  def generationShouldPass(gen : => Component): Unit ={
    assert(!checkFailure{SpinalVhdl(gen)})
    assert(!checkFailure{SpinalVerilog(gen)})
  }
}
