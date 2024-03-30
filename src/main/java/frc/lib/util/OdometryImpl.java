// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.lib.util;

import edu.wpi.first.math.VecBuilder;
import edu.wpi.first.math.Vector;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.numbers.N3;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Constants;
import frc.robot.RobotContainer;
import frc.robot.Constants.LimelightConstants;
import frc.robot.subsystems.Swerve;

public class OdometryImpl extends SubsystemBase {
  /** Creates a new OdometryImpl. */
  Swerve s_Swerve;

  // Counts total rejected vision measurements because of large error
  private int totalRejections = 0;

  public OdometryImpl(Swerve s_Swerve) {
      this.s_Swerve = s_Swerve;
  }
 
  public double getDistance(Pose2d target) {
    //changed from getRelativePose()
      double distance = s_Swerve.getRelativePose().getTranslation().getDistance(target.getTranslation());
      return distance;
  }

  public double getPivotAngle(DriverStation.Alliance alliance) {
    double base;
    double height = Constants.StructureConstants.speakerHeight - Constants.StructureConstants.shooterRobotHeight;
       
    if (alliance == DriverStation.Alliance.Red) {
        base = getDistance(Constants.RedTeamPoses.redSpeakerPose);
 
    }

    else {
        base = getDistance(Constants.BlueTeamPoses.blueSpeakerPose);
    }
        
    // System.out.println(Math.atan(height / base));
    return Math.atan(height / base); 


  }

  //This is assuming that the robot is directly facing the target object
  public double getTurnAngle(Pose2d target, double robotAngle) {
      double tx = target.getX();
      double ty = target.getY();
      double rx = s_Swerve.getPose().getX();
      double ry = s_Swerve.getPose().getY();

      double requestedAngle = Math.atan((ty - ry) / (tx - rx)) * (180/ Math.PI);
      double calculatedAngle = (180 - robotAngle + requestedAngle);

      return ((calculatedAngle + 180) % 360) - 180;
  }

  public double getVisionPoseError(Limelight limelight) {
    if(limelight == null || s_Swerve.poseEstimator == null) return -1;
      Pose2d predictedPose = limelight.getVisionPredictedRobotPose(); 
      if (predictedPose != null) {
         return Math.abs(s_Swerve.poseEstimator.getEstimatedPosition().getTranslation().getDistance(predictedPose.getTranslation()));
      }
      return -1;
  }

  public boolean isValidVisionMeasurement(Limelight limelight) {
    if(limelight == null) return false;
      Pose2d predictedPose = limelight.getVisionPredictedRobotPose();
      //deleted the predictedPose = null check 
      if (predictedPose != null && (predictedPose.getX() != 0 && predictedPose.getY() != 0)) {
          if ((limelight.getTagArea() > LimelightConstants.minAreaOfTag) || limelight.getNumberOfTagsInView() > 1) {
              //Newly added if it doesn't work take out
              return true;
              
          }
      }

      return false;
  }

  public Pose2d getVisionMeasurementWithoutYaw(Limelight limelight) {
    if(limelight == null) return null;
      //added variable for predicted pose instead of calling function directly
      Pose2d predictedPose = limelight.getVisionPredictedRobotPose();
      if (isValidVisionMeasurement(limelight) && predictedPose != null) {
          //.out.println("ADDED VISION MEASUREMENT");
          return new Pose2d(predictedPose.getTranslation(), s_Swerve.getHeading());
      }
 
      return null;
  } 

   
  public Pose2d getVisionMeasurement(Limelight limelight) {
    if(limelight == null) return null;
      Pose2d predictedPose = limelight.getVisionPredictedRobotPose();
      if (isValidVisionMeasurement(limelight) && predictedPose != null) {
          return predictedPose;
      }

      return null;
  }
  
  // Angle offset should be passed in as degrees
  public Vector<N3> createStdDevs(double n1, double n2, double angleOffset) {
      return VecBuilder.fill(n1, n2, Units.degreesToRadians(angleOffset));
  }

  public Vector<N3> getCalculatedStdDevs(Pose2d visionMeasurement, Limelight limelight) {
    double error = getVisionPoseError(limelight);
    double xyStds = 0;
    double thetaStds = 0;  
    if (limelight.getNumberOfTagsInView() >= 2) {
        xyStds = 0.5; 
        thetaStds = 6; 
    }
  
    else if (limelight.getNumberOfTagsInView() == 1) {
        //one tag with large area but larger pose error 
        if (limelight.getTagArea() > 0.8 && error < 0.5) {
          xyStds = 1; 
          thetaStds = 12; 
        }

        //one tag with small area but smaller pose error
        else if (limelight.getTagArea() > 0.1 && error < 0.3) {
          xyStds = 2; 
          thetaStds = 30; //basically disregard limelight pose 
        }

    }

    totalRejections++; // TODO dont forget this

    return VecBuilder.fill(xyStds, xyStds, Units.degreesToRadians(thetaStds)); 




  }

  public double getDistanceToSpeaker() {
    Pose2d speaker = RobotContainer.alliance == DriverStation.Alliance.Red ? Constants.RedTeamPoses.redSpeakerPose : Constants.BlueTeamPoses.blueSpeakerPose;
    return getDistance(speaker);
  }


  @Override
  public void periodic() {
      // This method will be called once per scheduler run
      // newly added
      // SmartDashboard.putNumber("Vision Pose Error Limelight Front", getVisionPoseError(s_Swerve.limelightShooter));
      // SmartDashboard.putNumber("Vision Pose Error Limelight Back", getVisionPoseError(s_Swerve.limelightArm));

      SmartDashboard.putNumber(
        (RobotContainer.alliance == DriverStation.Alliance.Red) ? "Red speaker distance" : "Blue speaker distance", 
        ((RobotContainer.alliance == DriverStation.Alliance.Red) ? getDistance(Constants.RedTeamPoses.redSpeakerPose) : getDistance(Constants.BlueTeamPoses.blueSpeakerPose))
      );

      SmartDashboard.putNumber("Calculated Angle from Odometry", getPivotAngle(RobotContainer.s_Swerve.alliance));
    }
}