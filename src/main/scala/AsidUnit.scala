package dana

import Chisel._

import rocket._

class ANTWRequest extends XFilesBundle {
  val antp = UInt(width = params(XLen))
  val size = UInt(width = params(XLen))
}

class AsidUnitANTWInterface extends XFilesBundle {
  val req = Decoupled(new ANTWRequest)
}

class AsidUnit extends DanaModule with XFilesParameters {
  val io = new XFilesBundle {
    val core = new XFilesBundle {
      val cmd = Valid(new RoCCCommand).flip
      val s = Bool(INPUT)
    }
    val antw = new AsidUnitANTWInterface
    val asid = UInt(OUTPUT, width = asidWidth)
    val tid = UInt(OUTPUT, width = tidWidth)
  }

  val asidReg = Reg(new XFilesBundle {
    val valid = Bool()
    val asid = UInt(width = asidWidth)
    val tid = UInt(width = tidWidth)
  })

  val updateAsid = io.core.s && io.core.cmd.bits.inst.funct === UInt(0)
  val updateANTP = io.core.s && io.core.cmd.bits.inst.funct === UInt(1)
  val newRequest = !io.core.s && io.core.cmd.bits.inst.funct(0) &&
    io.core.cmd.bits.inst.funct(1)

  // Defaults
  io.antw.req.valid := Bool(false)
  io.antw.req.bits.antp := UInt(0)
  io.antw.req.bits.size := UInt(0)

  // Snoop on the input RoCCInterface. When you see a new supervisory
  // ASID-update request, set the ASID and reset the TID counter.
  when (io.core.cmd.fire() && updateAsid) {
    asidReg.valid := Bool(true)
    asidReg.asid := io.core.cmd.bits.rs1(asidWidth - 1, 0)
    asidReg.tid := UInt(0)
    printf("[INFO] Saw supervisor request to update ASID to 0x%x\n",
      io.core.cmd.bits.rs1(asidWidth - 1, 0));
    // [TODO] This needs to respond to the core with the ASID and TID
    // so that the OS can save the ASID/TID for reloading later.
  }
  // Generate a request that updates the ASID--NNID Table Pointer if
  // we see this request on the RoCCInterface
  when (io.core.cmd.fire() && updateANTP) {
    io.antw.req.valid := Bool(true);
    io.antw.req.bits.antp := io.core.cmd.bits.rs1;
    io.antw.req.bits.size := io.core.cmd.bits.rs2;
    printf("[INFO] Saw supervisor request to change ANTP to 0x%x of size 0x%x\n",
      io.core.cmd.bits.rs1,
      io.core.cmd.bits.rs2);
  }
  when (io.core.cmd.fire() && newRequest) {
    asidReg.tid := asidReg.tid + UInt(1)
  }
  io.asid := asidReg.asid
  io.tid := asidReg.tid

  // Reset
  when (reset) {
    asidReg.valid := Bool(false)
  }

  // Assertions
  // There shouldn't be a new request on an invalid ASID
  assert(!(io.core.cmd.fire() && newRequest && !asidReg.valid),
    "New request on invalid ASID (a clean build may be needed)");
}