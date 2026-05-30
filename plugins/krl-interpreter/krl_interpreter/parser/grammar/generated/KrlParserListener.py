# Generated from KrlParser.g4 by ANTLR 4.13.2
from antlr4 import *
if "." in __name__:
    from .KrlParser import KrlParser
else:
    from KrlParser import KrlParser

# This class defines a complete listener for a parse tree produced by KrlParser.
class KrlParserListener(ParseTreeListener):

    # Enter a parse tree produced by KrlParser#program.
    def enterProgram(self, ctx:KrlParser.ProgramContext):
        pass

    # Exit a parse tree produced by KrlParser#program.
    def exitProgram(self, ctx:KrlParser.ProgramContext):
        pass


    # Enter a parse tree produced by KrlParser#srcModule.
    def enterSrcModule(self, ctx:KrlParser.SrcModuleContext):
        pass

    # Exit a parse tree produced by KrlParser#srcModule.
    def exitSrcModule(self, ctx:KrlParser.SrcModuleContext):
        pass


    # Enter a parse tree produced by KrlParser#datModule.
    def enterDatModule(self, ctx:KrlParser.DatModuleContext):
        pass

    # Exit a parse tree produced by KrlParser#datModule.
    def exitDatModule(self, ctx:KrlParser.DatModuleContext):
        pass


    # Enter a parse tree produced by KrlParser#paramList.
    def enterParamList(self, ctx:KrlParser.ParamListContext):
        pass

    # Exit a parse tree produced by KrlParser#paramList.
    def exitParamList(self, ctx:KrlParser.ParamListContext):
        pass


    # Enter a parse tree produced by KrlParser#paramDecl.
    def enterParamDecl(self, ctx:KrlParser.ParamDeclContext):
        pass

    # Exit a parse tree produced by KrlParser#paramDecl.
    def exitParamDecl(self, ctx:KrlParser.ParamDeclContext):
        pass


    # Enter a parse tree produced by KrlParser#datDeclBlock.
    def enterDatDeclBlock(self, ctx:KrlParser.DatDeclBlockContext):
        pass

    # Exit a parse tree produced by KrlParser#datDeclBlock.
    def exitDatDeclBlock(self, ctx:KrlParser.DatDeclBlockContext):
        pass


    # Enter a parse tree produced by KrlParser#statementBlock.
    def enterStatementBlock(self, ctx:KrlParser.StatementBlockContext):
        pass

    # Exit a parse tree produced by KrlParser#statementBlock.
    def exitStatementBlock(self, ctx:KrlParser.StatementBlockContext):
        pass


    # Enter a parse tree produced by KrlParser#statement.
    def enterStatement(self, ctx:KrlParser.StatementContext):
        pass

    # Exit a parse tree produced by KrlParser#statement.
    def exitStatement(self, ctx:KrlParser.StatementContext):
        pass


    # Enter a parse tree produced by KrlParser#ptpLinMotion.
    def enterPtpLinMotion(self, ctx:KrlParser.PtpLinMotionContext):
        pass

    # Exit a parse tree produced by KrlParser#ptpLinMotion.
    def exitPtpLinMotion(self, ctx:KrlParser.PtpLinMotionContext):
        pass


    # Enter a parse tree produced by KrlParser#circMotion.
    def enterCircMotion(self, ctx:KrlParser.CircMotionContext):
        pass

    # Exit a parse tree produced by KrlParser#circMotion.
    def exitCircMotion(self, ctx:KrlParser.CircMotionContext):
        pass


    # Enter a parse tree produced by KrlParser#motionOpts.
    def enterMotionOpts(self, ctx:KrlParser.MotionOptsContext):
        pass

    # Exit a parse tree produced by KrlParser#motionOpts.
    def exitMotionOpts(self, ctx:KrlParser.MotionOptsContext):
        pass


    # Enter a parse tree produced by KrlParser#poseExpr.
    def enterPoseExpr(self, ctx:KrlParser.PoseExprContext):
        pass

    # Exit a parse tree produced by KrlParser#poseExpr.
    def exitPoseExpr(self, ctx:KrlParser.PoseExprContext):
        pass


    # Enter a parse tree produced by KrlParser#waitSec.
    def enterWaitSec(self, ctx:KrlParser.WaitSecContext):
        pass

    # Exit a parse tree produced by KrlParser#waitSec.
    def exitWaitSec(self, ctx:KrlParser.WaitSecContext):
        pass


    # Enter a parse tree produced by KrlParser#waitFor.
    def enterWaitFor(self, ctx:KrlParser.WaitForContext):
        pass

    # Exit a parse tree produced by KrlParser#waitFor.
    def exitWaitFor(self, ctx:KrlParser.WaitForContext):
        pass


    # Enter a parse tree produced by KrlParser#ifStmt.
    def enterIfStmt(self, ctx:KrlParser.IfStmtContext):
        pass

    # Exit a parse tree produced by KrlParser#ifStmt.
    def exitIfStmt(self, ctx:KrlParser.IfStmtContext):
        pass


    # Enter a parse tree produced by KrlParser#forStmt.
    def enterForStmt(self, ctx:KrlParser.ForStmtContext):
        pass

    # Exit a parse tree produced by KrlParser#forStmt.
    def exitForStmt(self, ctx:KrlParser.ForStmtContext):
        pass


    # Enter a parse tree produced by KrlParser#whileStmt.
    def enterWhileStmt(self, ctx:KrlParser.WhileStmtContext):
        pass

    # Exit a parse tree produced by KrlParser#whileStmt.
    def exitWhileStmt(self, ctx:KrlParser.WhileStmtContext):
        pass


    # Enter a parse tree produced by KrlParser#loopStmt.
    def enterLoopStmt(self, ctx:KrlParser.LoopStmtContext):
        pass

    # Exit a parse tree produced by KrlParser#loopStmt.
    def exitLoopStmt(self, ctx:KrlParser.LoopStmtContext):
        pass


    # Enter a parse tree produced by KrlParser#exitStmt.
    def enterExitStmt(self, ctx:KrlParser.ExitStmtContext):
        pass

    # Exit a parse tree produced by KrlParser#exitStmt.
    def exitExitStmt(self, ctx:KrlParser.ExitStmtContext):
        pass


    # Enter a parse tree produced by KrlParser#frameSwitchStmt.
    def enterFrameSwitchStmt(self, ctx:KrlParser.FrameSwitchStmtContext):
        pass

    # Exit a parse tree produced by KrlParser#frameSwitchStmt.
    def exitFrameSwitchStmt(self, ctx:KrlParser.FrameSwitchStmtContext):
        pass


    # Enter a parse tree produced by KrlParser#varDecl.
    def enterVarDecl(self, ctx:KrlParser.VarDeclContext):
        pass

    # Exit a parse tree produced by KrlParser#varDecl.
    def exitVarDecl(self, ctx:KrlParser.VarDeclContext):
        pass


    # Enter a parse tree produced by KrlParser#typeName.
    def enterTypeName(self, ctx:KrlParser.TypeNameContext):
        pass

    # Exit a parse tree produced by KrlParser#typeName.
    def exitTypeName(self, ctx:KrlParser.TypeNameContext):
        pass


    # Enter a parse tree produced by KrlParser#assignStmt.
    def enterAssignStmt(self, ctx:KrlParser.AssignStmtContext):
        pass

    # Exit a parse tree produced by KrlParser#assignStmt.
    def exitAssignStmt(self, ctx:KrlParser.AssignStmtContext):
        pass


    # Enter a parse tree produced by KrlParser#bcoStmt.
    def enterBcoStmt(self, ctx:KrlParser.BcoStmtContext):
        pass

    # Exit a parse tree produced by KrlParser#bcoStmt.
    def exitBcoStmt(self, ctx:KrlParser.BcoStmtContext):
        pass


    # Enter a parse tree produced by KrlParser#spsStmt.
    def enterSpsStmt(self, ctx:KrlParser.SpsStmtContext):
        pass

    # Exit a parse tree produced by KrlParser#spsStmt.
    def exitSpsStmt(self, ctx:KrlParser.SpsStmtContext):
        pass


    # Enter a parse tree produced by KrlParser#interruptStmt.
    def enterInterruptStmt(self, ctx:KrlParser.InterruptStmtContext):
        pass

    # Exit a parse tree produced by KrlParser#interruptStmt.
    def exitInterruptStmt(self, ctx:KrlParser.InterruptStmtContext):
        pass


    # Enter a parse tree produced by KrlParser#onErrorStmt.
    def enterOnErrorStmt(self, ctx:KrlParser.OnErrorStmtContext):
        pass

    # Exit a parse tree produced by KrlParser#onErrorStmt.
    def exitOnErrorStmt(self, ctx:KrlParser.OnErrorStmtContext):
        pass


    # Enter a parse tree produced by KrlParser#aninStmt.
    def enterAninStmt(self, ctx:KrlParser.AninStmtContext):
        pass

    # Exit a parse tree produced by KrlParser#aninStmt.
    def exitAninStmt(self, ctx:KrlParser.AninStmtContext):
        pass


    # Enter a parse tree produced by KrlParser#anoutStmt.
    def enterAnoutStmt(self, ctx:KrlParser.AnoutStmtContext):
        pass

    # Exit a parse tree produced by KrlParser#anoutStmt.
    def exitAnoutStmt(self, ctx:KrlParser.AnoutStmtContext):
        pass


    # Enter a parse tree produced by KrlParser#continueStmt.
    def enterContinueStmt(self, ctx:KrlParser.ContinueStmtContext):
        pass

    # Exit a parse tree produced by KrlParser#continueStmt.
    def exitContinueStmt(self, ctx:KrlParser.ContinueStmtContext):
        pass


    # Enter a parse tree produced by KrlParser#haltStmt.
    def enterHaltStmt(self, ctx:KrlParser.HaltStmtContext):
        pass

    # Exit a parse tree produced by KrlParser#haltStmt.
    def exitHaltStmt(self, ctx:KrlParser.HaltStmtContext):
        pass


    # Enter a parse tree produced by KrlParser#frameLiteral.
    def enterFrameLiteral(self, ctx:KrlParser.FrameLiteralContext):
        pass

    # Exit a parse tree produced by KrlParser#frameLiteral.
    def exitFrameLiteral(self, ctx:KrlParser.FrameLiteralContext):
        pass


    # Enter a parse tree produced by KrlParser#frameField.
    def enterFrameField(self, ctx:KrlParser.FrameFieldContext):
        pass

    # Exit a parse tree produced by KrlParser#frameField.
    def exitFrameField(self, ctx:KrlParser.FrameFieldContext):
        pass


    # Enter a parse tree produced by KrlParser#expr.
    def enterExpr(self, ctx:KrlParser.ExprContext):
        pass

    # Exit a parse tree produced by KrlParser#expr.
    def exitExpr(self, ctx:KrlParser.ExprContext):
        pass


    # Enter a parse tree produced by KrlParser#orExpr.
    def enterOrExpr(self, ctx:KrlParser.OrExprContext):
        pass

    # Exit a parse tree produced by KrlParser#orExpr.
    def exitOrExpr(self, ctx:KrlParser.OrExprContext):
        pass


    # Enter a parse tree produced by KrlParser#andExpr.
    def enterAndExpr(self, ctx:KrlParser.AndExprContext):
        pass

    # Exit a parse tree produced by KrlParser#andExpr.
    def exitAndExpr(self, ctx:KrlParser.AndExprContext):
        pass


    # Enter a parse tree produced by KrlParser#notExpr.
    def enterNotExpr(self, ctx:KrlParser.NotExprContext):
        pass

    # Exit a parse tree produced by KrlParser#notExpr.
    def exitNotExpr(self, ctx:KrlParser.NotExprContext):
        pass


    # Enter a parse tree produced by KrlParser#cmpExpr.
    def enterCmpExpr(self, ctx:KrlParser.CmpExprContext):
        pass

    # Exit a parse tree produced by KrlParser#cmpExpr.
    def exitCmpExpr(self, ctx:KrlParser.CmpExprContext):
        pass


    # Enter a parse tree produced by KrlParser#addExpr.
    def enterAddExpr(self, ctx:KrlParser.AddExprContext):
        pass

    # Exit a parse tree produced by KrlParser#addExpr.
    def exitAddExpr(self, ctx:KrlParser.AddExprContext):
        pass


    # Enter a parse tree produced by KrlParser#mulExpr.
    def enterMulExpr(self, ctx:KrlParser.MulExprContext):
        pass

    # Exit a parse tree produced by KrlParser#mulExpr.
    def exitMulExpr(self, ctx:KrlParser.MulExprContext):
        pass


    # Enter a parse tree produced by KrlParser#unaryExpr.
    def enterUnaryExpr(self, ctx:KrlParser.UnaryExprContext):
        pass

    # Exit a parse tree produced by KrlParser#unaryExpr.
    def exitUnaryExpr(self, ctx:KrlParser.UnaryExprContext):
        pass


    # Enter a parse tree produced by KrlParser#atom.
    def enterAtom(self, ctx:KrlParser.AtomContext):
        pass

    # Exit a parse tree produced by KrlParser#atom.
    def exitAtom(self, ctx:KrlParser.AtomContext):
        pass



del KrlParser